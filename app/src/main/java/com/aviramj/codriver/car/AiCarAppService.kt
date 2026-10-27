package com.aviramj.codriver.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point the Android Auto host connects to. Accepts all hosts — for a
 * personal/internal-testing app this avoids a head unit being rejected by a
 * too-narrow allowlist. Tighten this before any public release.
 */
class AiCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = AiSession()
}
