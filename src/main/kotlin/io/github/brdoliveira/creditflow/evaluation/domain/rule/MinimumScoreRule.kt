package io.github.brdoliveira.creditflow.evaluation.domain.rule

import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluationContext
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import io.github.brdoliveira.creditflow.evaluation.domain.RuleSeverity
import io.github.brdoliveira.creditflow.evaluation.domain.RuleStatus

/** Bloqueia avaliações abaixo do score mínimo configurado. */
class MinimumScoreRule(private val minimumScore: Int = 650) : CreditRule {
    init {
        require(minimumScore in 0..1000)
    }

    override val code = "MINIMUM_SCORE"
    override val name = "Score mínimo"
    override val severity = RuleSeverity.BLOCKING

    /** Compara o score recebido ao limite mínimo. */
    override fun evaluate(context: CreditEvaluationContext): RuleResult {
        val passed = context.creditScore >= minimumScore
        return RuleResult(
            code,
            name,
            severity,
            if (passed) RuleStatus.PASSED else RuleStatus.FAILED,
            if (passed) "Score atende ao mínimo configurado" else "Score abaixo do mínimo configurado",
            mapOf("score" to context.creditScore.toString(), "minimumScore" to minimumScore.toString()),
        )
    }
}
