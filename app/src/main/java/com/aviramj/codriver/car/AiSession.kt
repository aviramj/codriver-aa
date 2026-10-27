package com.aviramj.codriver.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class AiSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = ConversationScreen(carContext)
}
