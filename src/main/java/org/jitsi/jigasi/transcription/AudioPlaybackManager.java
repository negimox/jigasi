/*
 * Jigasi, the JItsi GAteway to SIP.
 *
 * Copyright @ 2024-present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.jigasi.transcription;

import org.jitsi.impl.neomedia.device.AudioMixerMediaDevice;
import org.jitsi.jigasi.JigasiBundleActivator;
import org.jitsi.utils.logging.Logger;
import org.json.simple.JSONObject;

import javax.media.Buffer;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.SourceStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Manages the playback of translated audio into the conference.
 *
 * @author Jules
 */
public class AudioPlaybackManager
    implements TranslationResultListener
{
    /**
     * The logger for this class.
     */
    private final static Logger logger
        = Logger.getLogger(AudioPlaybackManager.class);

    /**
     * The config key for the text-to-speech API URL.
     */
    public static final String TTS_API_URL_CONFIG_KEY
        = "org.jitsi.jigasi.transcription.tts.api.url";

    private final AudioMixerMediaDevice mediaDevice;
    private final String ttsApiUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Creates a new AudioPlaybackManager.
     *
     * @param mediaDevice The media device to use for audio playback.
     */
    public AudioPlaybackManager(AudioMixerMediaDevice mediaDevice)
    {
        this.mediaDevice = mediaDevice;
        this.ttsApiUrl = JigasiBundleActivator.getConfigurationService()
            .getString(TTS_API_URL_CONFIG_KEY, null);
    }

    @Override
    public void notify(TranslationResult result)
    {
        if (ttsApiUrl == null || ttsApiUrl.isEmpty())
        {
            logger.warn("TTS API URL is not configured.");
            return;
        }

        try
        {
            JSONObject payload = new JSONObject();
            payload.put("input", result.getTranslatedText());
            // Assuming default voice and format for now
            payload.put("voice", "af_heart");
            payload.put("response_format", "wav");

            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(ttsApiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    if (response.statusCode() == 200)
                    {
                        byte[] audioData = response.body();
                        // playAudio(audioData);
                    }
                    else
                    {
                        logger.error("Error from TTS API: " + response.statusCode());
                    }
                });
        }
        catch (Exception e)
        {
            logger.error("Error sending request to TTS API", e);
        }
    }

    // private void playAudio(byte[] audioData)
    // {
    //     // try
    //     // {
    //     //     ByteArrayDataSource dataSource = new ByteArrayDataSource(audioData, "audio/wav");
    //     //     mediaDevice.addMediaStream(dataSource);
    //     // }
    //     // catch (Exception e)
    //     // {
    //     //     logger.error("Error playing audio", e);
    //     // }
    // }

    /**
     * A DataSource for a byte array.
     */
    private static class ByteArrayDataSource extends PushBufferDataSource
    {
        private final byte[] data;
        private final String contentType;
        private ByteArrayPushBufferStream stream;

        ByteArrayDataSource(byte[] data, String contentType)
        {
            this.data = data;
            this.contentType = contentType;
        }

        @Override
        public PushBufferStream[] getStreams()
        {
            if (stream == null)
            {
                stream = new ByteArrayPushBufferStream(data);
            }
            return new PushBufferStream[] { stream };
        }

        @Override
        public void connect() throws IOException {}

        @Override
        public void disconnect() {}

        @Override
        public String getContentType()
        {
            return contentType;
        }

        @Override
        public Object getControl(String s)
        {
            return null;
        }

        @Override
        public Object[] getControls()
        {
            return new Object[0];
        }

        public javax.media.Time getDuration()
        {
            return javax.media.Duration.DURATION_UNKNOWN;
        }

        @Override
        public void start() throws IOException {}

        @Override
        public void stop() throws IOException {}
    }

    /**
     * A PushBufferStream for a byte array.
     */
    private static class ByteArrayPushBufferStream implements PushBufferStream
    {
        private final byte[] data;
        private boolean finished = false;

        ByteArrayPushBufferStream(byte[] data)
        {
            this.data = data;
        }

        @Override
        public javax.media.Format getFormat()
        {
            // Assuming WAV format, 16kHz, 16-bit, mono
            return new javax.media.format.AudioFormat(
                javax.media.format.AudioFormat.LINEAR,
                16000,
                16,
                1,
                javax.media.format.AudioFormat.LITTLE_ENDIAN,
                javax.media.format.AudioFormat.SIGNED
            );
        }

        @Override
        public void read(Buffer buffer) throws IOException
        {
            if (finished)
            {
                buffer.setLength(0);
                buffer.setEOM(true);
                return;
            }

            buffer.setData(data);
            buffer.setOffset(0);
            buffer.setLength(data.length);
            buffer.setFormat(getFormat());
            finished = true;
        }

        @Override
        public void setTransferHandler(javax.media.protocol.BufferTransferHandler bufferTransferHandler)
        {
        }

        @Override
        public boolean endOfStream()
        {
            return finished;
        }

        @Override
        public javax.media.protocol.ContentDescriptor getContentDescriptor()
        {
            return new javax.media.protocol.ContentDescriptor(javax.media.protocol.ContentDescriptor.RAW);
        }

        @Override
        public long getContentLength()
        {
            return data.length;
        }

        @Override
        public Object getControl(String s)
        {
            return null;
        }

        @Override
        public Object[] getControls()
        {
            return new Object[0];
        }
    }
}
