package websockets

import org.springframework.stereotype.Component
import java.util.Random
import java.util.Scanner

/**
 * Eliza.java - a simplified version of Joseph Weizenbaum's Eliza
 *
 *
 * Adapted from https://code.google.com/p/simple-semantic-desktop/source/browse/trunk/Progs2/Eliza/eliza.java
 * created by Akshat Singhal
 *
 * @Component mark this class as a Spring bean for dependency injection
 */
@Component
class Eliza {
    private var responses: Map<String, List<String>>
    private var keywords: Set<String>
    private val random = Random()

    init {
        val familyAnswers =
            listOf(
                "Tell me more about your family.",
                "How do you get along with your family?",
                "Is your family important to you?",
            )
        val iamAnswers =
            listOf(
                "I am sorry to hear you are *.",
                "How long have you been *?",
                "Do you believe it is normal to be *?",
                "Do you enjoy being *?",
            )
        val dreamAnswers =
            listOf(
                "What does that dream suggest to you?",
                "Do you dream often?",
                "What persons appear in your dreams?",
                "Are you disturbed by your dreams?",
            )
        responses =
            mapOf(
                NOT_FOUND to
                    listOf(
                        "What does that suggest to you?",
                        "I see.",
                        "I'm not sure I understand you fully.",
                        "Can you elaborate?",
                        "That is quite interesting.",
                    ),
                "always" to listOf("Can you think of a specific example?"),
                "because" to listOf("Is that the real reason?"),
                "sorry" to listOf("Please don't apologize."),
                "maybe" to listOf("You don't seem very certain."),
                "i think" to listOf("Do you really think so?"),
                "you" to listOf("We were discussing you, not me."),
                "yes" to listOf("Why do you think so?", "You seem quite positive."),
                "no" to listOf("Why not?", "Are you sure?"),
                "i am" to iamAnswers,
                "i'm" to iamAnswers,
                "i feel" to
                    listOf(
                        "Tell me more about such feelings.",
                        "Do you often feel *?",
                        "Do you enjoy feeling *?",
                        "Why do you feel that way?",
                    ),
                "family" to familyAnswers,
                "mother" to familyAnswers,
                "father" to familyAnswers,
                "mom" to familyAnswers,
                "dad" to familyAnswers,
                "sister" to familyAnswers,
                "brother" to familyAnswers,
                "husband" to familyAnswers,
                "wife" to familyAnswers,
                "dream" to dreamAnswers,
                "nightmare" to dreamAnswers,
            )
        keywords = responses.keys
    }

    fun respond(s: Scanner): String {
        val keyword = keywords.firstOrNull { s.findInLine(it) != null } ?: NOT_FOUND
        val responseList = responses.getValue(keyword)
        val response = responseList[random.nextInt(responseList.size)]
        val hasSpread = response.indexOf('*') != -1
        val hasNextToken = s.hasNext()
        return when {
            hasSpread && hasNextToken -> {
                val remainingInput = s.nextLine().trim()
                val replacement = remainingInput.dropLastWhile { it !in DROP_REGEX }
                response.replaceFirst("*", replacement).trim { it <= ' ' }
            }
            hasSpread -> response.replace("*", "")
            else -> response
        }
    }

    companion object {
        const val NOT_FOUND = "NOTFOUND"
        val DROP_REGEX = ('A'..'Z') + ('a'..'z')
    }
}
