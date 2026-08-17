package io.github.brdoliveira.creditflow.infrastructure.idempotency

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

/** Stable SHA-256 over JSON where whitespace and object member ordering have no meaning. */
class CanonicalRequestHasher(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    fun hash(requestBody: String): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalize(requestBody).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun canonicalize(requestBody: String): String = canonicalize(objectMapper.readTree(requestBody))

    private fun canonicalize(node: JsonNode): String = when {
        node.isObject -> node.properties().asSequence().toList().sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}", separator = ",") { (name, value) ->
                "${objectMapper.writeValueAsString(name)}:${canonicalize(value)}"
            }
        node.isArray -> node.values().asSequence().joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::canonicalize)
        node.isTextual -> objectMapper.writeValueAsString(node.textValue())
        node.isNumber -> node.decimalValue().stripTrailingZeros().toPlainString()
        node.isBoolean -> node.booleanValue().toString()
        node.isNull -> "null"
        else -> throw IllegalArgumentException("Unsupported JSON value")
    }
}
