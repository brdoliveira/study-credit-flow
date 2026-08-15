package com.itau.credit.infrastructure.persistence

import com.itau.credit.application.port.CreditEvaluationFilter
import com.itau.credit.application.port.CreditEvaluationPage
import com.itau.credit.application.port.CreditEvaluationPageRequest
import com.itau.credit.application.port.CreditEvaluationRepository
import com.itau.credit.application.port.CreditEvaluationSnapshot
import com.itau.credit.application.port.CreditEvaluationSort
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class PostgresCreditEvaluationRepository(
    private val entityManager: EntityManager,
) : CreditEvaluationRepository {
    @Transactional
    override fun save(snapshot: CreditEvaluationSnapshot): CreditEvaluationSnapshot {
        entityManager.persist(CreditEvaluationEntity.from(snapshot))
        return snapshot
    }

    @Transactional(readOnly = true)
    override fun findById(evaluationId: UUID): CreditEvaluationSnapshot? =
        entityManager.find(CreditEvaluationEntity::class.java, evaluationId)?.toSnapshot()

    @Transactional(readOnly = true)
    override fun findPage(filter: CreditEvaluationFilter, page: CreditEvaluationPageRequest): CreditEvaluationPage {
        val criteriaBuilder = entityManager.criteriaBuilder
        val criteriaQuery = criteriaBuilder.createQuery(CreditEvaluationEntity::class.java)
        val root = criteriaQuery.from(CreditEvaluationEntity::class.java)
        val predicates = predicates(filter, root, criteriaBuilder)
        criteriaQuery.where(*predicates.toTypedArray())
        criteriaQuery.orderBy(
            if (page.sort == CreditEvaluationSort.EVALUATED_AT_ASC) {
                criteriaBuilder.asc(root.get<java.time.Instant>("evaluatedAt"))
            } else {
                criteriaBuilder.desc(root.get<java.time.Instant>("evaluatedAt"))
            },
        )

        val items = entityManager.createQuery(criteriaQuery)
            .setFirstResult(page.page * page.size)
            .setMaxResults(page.size)
            .resultList
            .map(CreditEvaluationEntity::toSnapshot)

        val countQuery = criteriaBuilder.createQuery(Long::class.java)
        val countRoot = countQuery.from(CreditEvaluationEntity::class.java)
        countQuery.select(criteriaBuilder.count(countRoot))
            .where(*predicates(filter, countRoot, criteriaBuilder).toTypedArray())

        return CreditEvaluationPage(items, entityManager.createQuery(countQuery).singleResult, page.page, page.size, page.sort)
    }

    private fun predicates(
        filter: CreditEvaluationFilter,
        root: jakarta.persistence.criteria.Root<CreditEvaluationEntity>,
        criteriaBuilder: jakarta.persistence.criteria.CriteriaBuilder,
    ): List<Predicate> = buildList {
        filter.decision?.let { add(criteriaBuilder.equal(root.get<String>("decision"), it)) }
        filter.from?.let { add(criteriaBuilder.greaterThanOrEqualTo(root.get<java.time.Instant>("evaluatedAt"), it)) }
        filter.to?.let { add(criteriaBuilder.lessThanOrEqualTo(root.get<java.time.Instant>("evaluatedAt"), it)) }
    }
}
