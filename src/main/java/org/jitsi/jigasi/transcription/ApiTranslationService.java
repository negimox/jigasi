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
    public String translate(String sourceText, String sourceLang, String targetLang)
    {
        // The ApiTranscriptionService already returns the translated text.
        // This service just passes it through.
        return sourceText;
    }

    /**
     * A name for this translation service
     *
     * @return A String which is a human-readable name for this service
     */
    public String getServiceDisplayName()
    {
        return "API Translation Service";
    }
}
