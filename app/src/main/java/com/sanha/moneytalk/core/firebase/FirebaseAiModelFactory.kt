package com.sanha.moneytalk.core.firebase

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import javax.inject.Inject
import javax.inject.Singleton

/** Creates App Check-protected Gemini models through Firebase AI Logic. */
@Singleton
class FirebaseAiModelFactory @Inject constructor() {

    private val firebaseAi by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    fun create(
        modelName: String,
        generationConfig: GenerationConfig? = null,
        systemInstruction: Content? = null,
        requestOptions: RequestOptions = RequestOptions()
    ): GenerativeModel {
        return firebaseAi.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig,
            systemInstruction = systemInstruction,
            requestOptions = requestOptions
        )
    }
}
