package com.example.Hami

import android.content.Context
import android.util.Log
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import org.tensorflow.lite.Interpreter

/*
   Text classification engine for detecting offensive content and hate speech in Arabic text.
   Uses a TensorFlow Lite model with BERT-based tokenization to classify text into 5 categories:
   Neutral, Offensive, Sexism, Religious Discrimination, and Racism.
 */
class AiEngine(private val context: Context) {

    private val maxSeqLen = 128
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vocabDictionary = mutableMapOf<String, Int>()
    private val reverseVocab = mutableMapOf<Int, String>()

    private var tflite: Interpreter? = null

    private val labels = arrayOf("Neutral", "Offensive", "Sexism", "Religious Discrimination", "Racism")

    init {
        loadModel()
        loadVocabulary()
    }


    /*
      Loads the WordPiece vocabulary from tokenizer.json in app assets.
      Populates both forward (word->id) and reverse (id->word) mappings.
      Special tokens: [CLS] (classification start), [SEP] (segment separator),
      [UNK] (unknown word), [PAD] (padding token).
     */
    private fun loadVocabulary() {
        try {
            val jsonString = context.assets.open("tokenizer.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)

            val modelObject = jsonObject.getJSONObject("model")
            val vocabObject = modelObject.getJSONObject("vocab")

            vocabObject.keys().forEach { word ->
                val id = vocabObject.getInt(word)
                vocabDictionary[word] = id
                reverseVocab[id] = word
            }
            Log.d("HamiTrace", "📖 [VOCAB]: Loaded ${vocabDictionary.size} tokens")
            Log.d("HamiTrace", "📖 [CLS] ID: ${vocabDictionary["[CLS]"]}")
            Log.d("HamiTrace", "📖 [SEP] ID: ${vocabDictionary["[SEP]"]}")
            Log.d("HamiTrace", "📖 [UNK] ID: ${vocabDictionary["[UNK]"]}")
            Log.d("HamiTrace", "📖 [PAD] ID: ${vocabDictionary["[PAD]"]}")
        } catch (e: Exception) {
            Log.e("HamiTrace", "❌ [VOCAB ERROR]: ${e.message}")
        }
    }
    /*
      Loads the TensorFlow Lite model from assets using memory-mapped file I/O.
      Model expects 3 input tensors: attention mask (Long[1][128]),
      input IDs (Long[1][128]), and token type IDs (Long[1][128]).
      Output is Float[1][5] logits for the 5 classification categories.
     */
    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd("model.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val buffer = inputStream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )

            val options = Interpreter.Options()
            tflite = Interpreter(buffer, options)

            val inputTensor0 = tflite?.getInputTensor(0)
            val inputTensor1 = tflite?.getInputTensor(1)
            val inputTensor2 = tflite?.getInputTensor(2)

            Log.d("HamiTrace", "✅ [LOAD]: Model loaded successfully")
            Log.d("HamiTrace", "🔧 Input 0: name=${inputTensor0?.name()}, shape=${inputTensor0?.shape()?.joinToString()}, type=${inputTensor0?.dataType()}")
            Log.d("HamiTrace", "🔧 Input 1: name=${inputTensor1?.name()}, shape=${inputTensor1?.shape()?.joinToString()}, type=${inputTensor1?.dataType()}")
            Log.d("HamiTrace", "🔧 Input 2: name=${inputTensor2?.name()}, shape=${inputTensor2?.shape()?.joinToString()}, type=${inputTensor2?.dataType()}")
        } catch (e: Exception) {
            Log.e("HamiTrace", "❌ [LOAD ERROR]: ${e.message}")
        }
    }
    /*
      Processes input text and returns classification result via callback.
      Workflow: normalize -> tokenize -> create attention mask -> run inference -> softmax -> callback

      @param text Input Arabic text to classify (can be empty, will be ignored)
      @param callback Returns (predictedLabel, confidenceScore) on main thread.
                      Returns ("Neutral", 0f) on error.
     */
    fun process(text: String, callback: (String, Float) -> Unit) {
        Log.d("HamiTrace", "=".repeat(50))
        Log.d("HamiTrace", "🔴 PROCESS CALLED with text: '$text'")

        if (text.isBlank()) {
            Log.d("HamiTrace", "🔴 Text is blank, skipping")
            return
        }

        executor.execute {
            try {
                // Step 1: Normalize text
                val normalizedText = normalize(text)
                Log.d("HamiTrace", "📝 Normalized: \"$normalizedText\"")

                // Step 2: Tokenize - returns Pair(tokens, actualLength)
                val (paddedTokens, actualLength) = tokenize(normalizedText)
                Log.d("HamiTrace", "📏 Actual token count (BEFORE padding): $actualLength")
                Log.d("HamiTrace", "🔢 Token IDs (first 20): ${paddedTokens.take(20)}")

                // Step 3: Show actual tokens
                val tokenWords = paddedTokens.take(actualLength).mapNotNull { reverseVocab[it] }
                Log.d("HamiTrace", "🔤 Actual tokens: $tokenWords")

                // Step 4: Create mask based on ACTUAL length
                val maskArray = LongArray(maxSeqLen) { if (it < actualLength) 1L else 0L }
                val maskBuffer = java.nio.LongBuffer.wrap(maskArray)
                Log.d("HamiTrace", "🎭 Mask (first 20): ${maskArray.take(20)}")

                // Step 5: Prepare input IDs (already padded from tokenize function)
                val inputLongArray = paddedTokens.map { it.toLong() }.toLongArray()
                val inputBuffer = java.nio.LongBuffer.wrap(inputLongArray)
                Log.d("HamiTrace", "📥 Input IDs (first 20): ${paddedTokens.take(20)}")

                // Step 6: Type buffer (all zeros)
                val typeArray = LongArray(maxSeqLen) { 0L }
                val typeBuffer = java.nio.LongBuffer.wrap(typeArray)
                Log.d("HamiTrace", "🔖 Type buffer (first 20): ${typeArray.take(20)}")

                // Step 7: Run inference with 3 inputs (mask, ids, type)
                val inputs = arrayOf(maskBuffer, inputBuffer, typeBuffer)
                val outputs = mutableMapOf<Int, Any>()
                outputs[0] = Array(1) { FloatArray(labels.size) }

                Log.d("HamiTrace", "🚀 Running inference...")
                tflite?.runForMultipleInputsOutputs(inputs, outputs)

                // Step 8: Get raw output
                val rawOutput = (outputs[0] as Array<FloatArray>)[0]
                Log.d("HamiTrace", "📊 Raw logits: ${rawOutput.joinToString { "%.4f".format(it) }}")

                // Step 9: Apply softmax
                val probabilities = softmax(rawOutput)
                Log.d("HamiTrace", "📊 Probabilities:")
                for (i in labels.indices) {
                    Log.d("HamiTrace", "   ${labels[i]}: ${"%.2f".format(probabilities[i] * 100)}%")
                }

                // Step 10: Find the highest probability
                val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
                val confidence = probabilities[maxIndex]
                val predictedLabel = labels[maxIndex]

                Log.d("HamiTrace", "🎯 Result: $predictedLabel with ${"%.2f".format(confidence * 100)}%")
                Log.d("HamiTrace", "=".repeat(50))

                mainHandler.post {
                    callback(predictedLabel, confidence)
                }

            } catch (e: Exception) {
                Log.e("HamiTrace", "❌ Error: ${e.message}")
                e.printStackTrace()
                mainHandler.post { callback("Neutral", 0f) }
            }
        }
    }

    /*
      Normalizes Arabic text by removing diacritics, URLs, mentions, hashtags,
      non-Arabic characters, and extra whitespace.

      @param text Raw input text
      @return Cleaned text suitable for tokenization
     */
    private fun normalize(text: String): String {
        return text
            .replace("[\\u064B-\\u0652]".toRegex(), "")
            .replace("http\\S+|@\\S+|#\\S+".toRegex(), "")
            .replace("[^\\u0621-\\u064A\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    /*
      Tokenizes text using WordPiece algorithm with [CLS] and [SEP] markers.

      @param text Normalized input text
      @return Pair containing:
              - List of token IDs padded to maxSeqLen (0 = [PAD])
              - Actual number of tokens before padding (used for attention mask)
     */
    private fun tokenize(text: String): Pair<List<Int>, Int> {
        val tokens = mutableListOf<Int>()

        // Add [CLS]
        tokens.add(vocabDictionary["[CLS]"] ?: 2)

        val words = text.split("\\s+".toRegex())
        for (word in words) {
            if (word.isEmpty()) continue
            var start = 0
            while (start < word.length) {
                var end = word.length
                var found = false
                while (end > start) {
                    var sub = word.substring(start, end)
                    if (start > 0) sub = "##$sub"
                    if (vocabDictionary.containsKey(sub)) {
                        tokens.add(vocabDictionary[sub]!!)
                        start = end
                        found = true
                        break
                    }
                    end--
                }
                if (!found) {
                    tokens.add(vocabDictionary["[UNK]"] ?: 1)
                    break
                }
            }
        }

        // Add [SEP]
        tokens.add(vocabDictionary["[SEP]"] ?: 3)

        // Save actual length BEFORE padding
        val actualLength = tokens.size
        Log.d("HamiTrace", "   Actual tokens before padding: $actualLength")
        Log.d("HamiTrace", "   Raw tokens: $tokens")

        // Pad to maxSeqLen
        while (tokens.size < maxSeqLen) {
            tokens.add(0) // [PAD] token
        }

        // Return padded tokens AND actual length
        return Pair(tokens.take(maxSeqLen), actualLength)
    }
    /*
      Applies softmax (Activation function) to convert logits to probability distribution.
      Uses max subtraction for numerical stability.

      @param logits Raw model outputs (size 5)
      @return Probability distribution where sum = 1.0
     */
    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.maxOrNull() ?: 0f
        val exps = logits.map { kotlin.math.exp((it - max).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }
}