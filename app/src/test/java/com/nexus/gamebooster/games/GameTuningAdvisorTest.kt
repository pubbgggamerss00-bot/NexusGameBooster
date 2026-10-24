package com.nexus.gamebooster.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameTuningAdvisorTest {
    @Test fun identifiesPubgNamesAndPackages() {
        assertEquals(GameTuningAdvisor.Title.PUBG_MOBILE, GameTuningAdvisor.identify("PUBG MOBILE"))
        assertEquals(GameTuningAdvisor.Title.PUBG_MOBILE, GameTuningAdvisor.identify("com.tencent.ig"))
        assertEquals(GameTuningAdvisor.Title.PUBG_MOBILE, GameTuningAdvisor.identify("BGMI"))
    }

    @Test fun identifiesCallOfDutyNamesAndPackages() {
        assertEquals(GameTuningAdvisor.Title.CALL_OF_DUTY_MOBILE, GameTuningAdvisor.identify("Call of Duty: Mobile"))
        assertEquals(GameTuningAdvisor.Title.CALL_OF_DUTY_MOBILE, GameTuningAdvisor.identify("com.activision.callofduty.shooter"))
        assertEquals(GameTuningAdvisor.Title.CALL_OF_DUTY_MOBILE, GameTuningAdvisor.identify("CODM"))
    }

    @Test fun unknownOrBlankNamesDoNotReceiveGameSpecificAdvice() {
        assertNull(GameTuningAdvisor.identify(null))
        assertNull(GameTuningAdvisor.identify(""))
        assertNull(GameTuningAdvisor.identify("Calculator"))
    }
}
