package cloud.angora.service

import cloud.angora.auth.ServicePrincipal
import cloud.angora.repository.ServiceTokenRepository
import org.slf4j.LoggerFactory
import java.time.Instant

interface ServiceTokenService {
    fun verify(token: String): ServicePrincipal?

    /**
     * Stores the hash of a configured token at startup. A blank or absent value
     * is a no-op, so an install running no bots needs no configuration.
     */
    fun register(name: String, token: String?)
}

class ServiceTokenServiceImpl(
    private val serviceTokenRepository: ServiceTokenRepository,
    private val tokenService: TokenService
) : ServiceTokenService {

    private val logger = LoggerFactory.getLogger(ServiceTokenServiceImpl::class.java)

    override fun verify(token: String): ServicePrincipal? {
        val record = serviceTokenRepository.findActiveByHash(tokenService.hash(token)) ?: return null

        serviceTokenRepository.touchLastUsed(record.id, Instant.now())

        return ServicePrincipal(tokenId = record.id, name = record.name)
    }

    override fun register(name: String, token: String?) {
        if (token.isNullOrBlank()) {
            logger.warn(
                "No service token configured for '{}' — routes requiring it will reject every caller",
                name
            )
            return
        }

        serviceTokenRepository.upsertByName(name, tokenService.hash(token))
        logger.info("Registered service token for '{}'", name)
    }
}
