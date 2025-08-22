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

import org.eclipse.jetty.websocket.api.*;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.jitsi.jigasi.*;
import org.jitsi.utils.logging.*;

import javax.media.format.*;
import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * A TranscriptionService which uses an external API for speech-to-text
 * and translation.
 *
 * @author Jules
 */
public class ApiTranscriptionService
    extends AbstractTranscriptionService
{
    /**
     * The logger for this class
     */
    private final static Logger logger
        = Logger.getLogger(ApiTranscriptionService.class);

    /**
     * The config key for the API endpoint.
     */
    public final static String API_URL_CONFIG_KEY
        = "org.jitsi.jigasi.transcription.api.url";

    /**
     * The API endpoint URL.
     */
    private String apiUrl;

    /**
     * The config key for the API username.
     */
    public final static String API_USERNAME_CONFIG_KEY
        = "org.jitsi.jigasi.transcription.api.username";

    /**
     * The config key for the API password.
     */
    public final static String API_PASSWORD_CONFIG_KEY
        = "org.jitsi.jigasi.transcription.api.password";

    /**
     * The API username.
     */
    private String apiUsername;

    /**
     * The API password.
     */
    private String apiPassword;

    /**
     * Creates a new ApiTranscriptionService.
     */
    public ApiTranscriptionService()
    {
        apiUrl = JigasiBundleActivator.getConfigurationService()
            .getString(API_URL_CONFIG_KEY, null);
        apiUsername = JigasiBundleActivator.getConfigurationService()
            .getString(API_USERNAME_CONFIG_KEY, null);
        apiPassword = JigasiBundleActivator.getConfigurationService()
            .getString(API_PASSWORD_CONFIG_KEY, null);
    }

    @Override
    public boolean isConfiguredProperly()
    {
        return apiUrl != null && !apiUrl.isEmpty();
    }

    @Override
    public boolean supportsLanguageRouting()
    {
        return false;
    }

    @Override
    public StreamingRecognitionSession initStreamingSession(Participant participant)
        throws UnsupportedOperationException
    {
        return new ApiStreamRecognitionSession(participant.getDebugName());
    }

    @Override
    public boolean supportsFragmentTranscription()
    {
        return true;
    }

    @Override
    public boolean supportsStreamRecognition()
    {
        return true;
    }

    @Override
    public void sendSingleRequest(TranscriptionRequest request, Consumer<TranscriptionResult> resultConsumer)
    {
        // This service only supports streaming
        throw new UnsupportedOperationException("Only streaming is supported");
    }

    /**
     * A streaming session for the API transcription service.
     */
    private class ApiStreamRecognitionSession
        implements StreamingRecognitionSession
    {
        private final String debugName;
        private final List<TranscriptionListener> listeners = new ArrayList<>();
        private boolean ended = false;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private AudioFormat audioFormat;
        private final HttpClient httpClient = HttpClient.newHttpClient();
        private final JSONParser jsonParser = new JSONParser();
        private static final int BUFFER_SIZE = 100 * 1024; // 100KB

        ApiStreamRecognitionSession(String debugName)
        {
            this.debugName = debugName;
        }

        @Override
        public void sendRequest(TranscriptionRequest request)
        {
            if (ended)
            {
                return;
            }

            if (audioFormat == null)
            {
                audioFormat = request.getFormat();
            }

            try
            {
                buffer.write(request.getAudio());
            }
            catch (IOException e)
            {
                logger.error("Error writing to buffer", e);
            }

            if (buffer.size() >= BUFFER_SIZE)
            {
                sendAudioToApi();
            }
        }

        @Override
        public void addTranscriptionListener(TranscriptionListener listener)
        {
            listeners.add(listener);
        }

        @Override
        public void end()
        {
            if (ended)
            {
                return;
            }
            ended = true;
            if (buffer.size() > 0)
            {
                sendAudioToApi();
            }
        }

        @Override
        public boolean ended()
        {
            return ended;
        }

        private void sendAudioToApi()
        {
            byte[] audioData = buffer.toByteArray();
            buffer.reset();

            try
            {
                String boundary = "Boundary-" + System.currentTimeMillis();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .header("Content-Type", "multipart/form-data;boundary=" + boundary);

                if (apiUsername != null && !apiUsername.isEmpty() && apiPassword != null && !apiPassword.isEmpty())
                {
                    String auth = apiUsername + ":" + apiPassword;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
                    requestBuilder.header("Authorization", "Basic " + encodedAuth);
                }

                HttpRequest request = requestBuilder.POST(ofMimeMultipartData(audioData, boundary))
                    .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        try
                        {
                            if (response.statusCode() != 200)
                            {
                                logger.error("Error from API: " + response.body());
                                return;
                            }
                            JSONObject json = (JSONObject) jsonParser.parse(response.body());
                            String translatedText = (String) json.get("asr_result");

                            if (translatedText != null && !translatedText.isEmpty())
                            {
                                TranscriptionResult result = new TranscriptionResult(
                                    null,
                                    UUID.randomUUID(),
                                    Instant.now(),
                                    false,
                                    "en-US", // Assuming target language is always English
                                    1.0,
                                    new TranscriptionAlternative(translatedText));

                                for (TranscriptionListener listener : listeners)
                                {
                                    listener.notify(result);
                                }
                            }
                        }
                        catch (ParseException e)
                        {
                            logger.error("Error parsing response from API", e);
                        }
                    });
            }
            catch (Exception e)
            {
                logger.error("Error sending request to API", e);
            }
        }

        private HttpRequest.BodyPublisher ofMimeMultipartData(byte[] data, String boundary)
            throws IOException
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"audio_file\"; filename=\"audio.raw\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n";
            baos.write(header.getBytes());
            baos.write(data);
            baos.write(("\r\n--" + boundary + "--\r\n").getBytes());
            return HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray());
        }
    }
}
