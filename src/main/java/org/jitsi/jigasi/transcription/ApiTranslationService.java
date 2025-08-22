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

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * A pass-through TranslationService that doesn't perform any translation.
 * It assumes that the transcription service already provides the translated text.
 *
 * @author Jules
 */
public class ApiTranslationService
    implements TranslationService
{
    @Override
    public Future<?> send(TranscriptionResult transcriptionResult, String sourceLanguage, String targetLanguage, Consumer<TranslationResult> resultConsumer)
    {
        // The ApiTranscriptionService already returns the translated text.
        // This service just passes it through.
        resultConsumer.accept(new TranslationResult(
            transcriptionResult.getParticipant(),
            targetLanguage,
            transcriptionResult.getAlternatives().get(0).getTranscription()
        ));
        return null;
    }

    @Override
    public String getServiceDisplayName() {
        return "API Translation Service";
    }
}
