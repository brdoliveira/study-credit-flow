package io.github.brdoliveira.creditflow.evaluation.infrastructure.persistence

import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationFilter
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPage
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationPageRequest
import io.github.brdoliveira.creditflow.evaluation.application.CreditEvaluationSort
import io.github.brdoliveira.creditflow.evaluation.application.port.CreditEvaluationRepository
import io.github.brdoliveira.creditflow.evaluation.domain.CreditEvaluation
import io.github.brdoliveira.creditflow.evaluation.domain.RuleResult
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/** Implementa a porta de avaliações usando a tabela PostgreSQL existente. */
@Repository
class PostgresCreditEvaluationRepository(
    private val entityManager: EntityManager,
    private val objectMapper: ObjectMapper,
) : CreditEvaluationRepository {
    /** Persiste o modelo consolidado sem expor a entidade à aplicação. */
    @Transactional
    override fun save(evaluation: CreditEvaluation): CreditEvaluation {
        entityManager.persist(
            CreditEvaluationEntity(
                evaluation.evaluationId,
                evaluation.maskedCpf,
                evaluation.decision.name,
                evaluation.approvedAmount,
                evaluation.ruleSetVersion,
                objectMapper.writeValueAsString(evaluation.ruleResults),
                evaluation.processedAt,
                evaluation.processingTimeMs,
                evaluation.correlationId,
            ),
        )
        return evaluation
    }

    /** Busca uma avaliação por identificador. */
    @Transactional(readOnly = true)
    override fun findById(evaluationId: UUID): CreditEvaluation? =
        entityManager.find(CreditEvaluationEntity::class.java, evaluationId)?.let { entity ->
            entity.toDomain(readRules(entity.ruleResults))
        }

    /** Lista avaliações com filtros, paginação e ordenação estável. */
    @Transactional(readOnly = true)
    override fun findPage(
        filter: CreditEvaluationFilter,
        page: CreditEvaluationPageRequest,
    ): CreditEvaluationPage {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(CreditEvaluationEntity::class.java)
        val root = query.from(CreditEvaluationEntity::class.java)
        query.where(*predicates(filter, root, criteriaBuilder).toTypedArray())
        query.orderBy(order(page.sort, root, criteriaBuilder), criteriaBuilder.desc(root.get<UUID>("evaluationId")))

        val items = entityManager.createQuery(query)
            .setFirstResult(page.page * page.size)
            .setMaxResults(page.size)
            .resultList
            .map { it.toDomain(readRules(it.ruleResults)) }
        return CreditEvaluationPage(items, count(filter, criteriaBuilder), page.page, page.size, page.sort)
    }

    private fun count(filter: CreditEvaluationFilter, criteriaBuilder: CriteriaBuilder): Long {
        val query = criteriaBuilder.createQuery(Long::class.java)
        val root = query.from(CreditEvaluationEntity::class.java)
        query.select(criteriaBuilder.count(root))
            .where(*predicates(filter, root, criteriaBuilder).toTypedArray())
        return entityManager.createQuery(query).singleResult
    }

    private fun order(sort: CreditEvaluationSort, root: Root<CreditEvaluationEntity>, cb: CriteriaBuilder) =
        when (sort) {
            CreditEvaluationSort.EVALUATED_AT_ASC -> cb.asc(root.get<Any>("evaluatedAt"))
            CreditEvaluationSort.EVALUATED_AT_DESC -> cb.desc(root.get<Any>("evaluatedAt"))
            CreditEvaluationSort.DECISION_ASC -> cb.asc(root.get<String>("decision"))
            CreditEvaluationSort.DECISION_DESC -> cb.desc(root.get<String>("decision"))
            CreditEvaluationSort.APPROVED_AMOUNT_ASC -> cb.asc(root.get<Any>("approvedAmount"))
            CreditEvaluationSort.APPROVED_AMOUNT_DESC -> cb.desc(root.get<Any>("approvedAmount"))
        }

    private fun predicates(
        filter: CreditEvaluationFilter,
        root: Root<CreditEvaluationEntity>,
        cb: CriteriaBuilder,
    ): List<Predicate> = buildList {
        filter.decision?.let { add(cb.equal(root.get<String>("decision"), it.name)) }
        filter.from?.let { add(cb.greaterThanOrEqualTo(root.get("evaluatedAt"), it)) }
        filter.to?.let { add(cb.lessThanOrEqualTo(root.get("evaluatedAt"), it)) }
    }

    private fun readRules(value: String) =
        objectMapper.readValue(value, object : TypeReference<List<RuleResult>>() {})
}
