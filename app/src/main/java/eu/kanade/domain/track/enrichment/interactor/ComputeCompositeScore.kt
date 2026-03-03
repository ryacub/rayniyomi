package eu.kanade.domain.track.enrichment.interactor

class ComputeCompositeScore {
    operator fun invoke(scores: List<Double>): Double? {
        val normalized = scores.filter { it > 0.0 }
        if (normalized.isEmpty()) return null
        return normalized.average()
    }
}
