package org.dba.nvlcheck

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.stage.Stage

class NVLCheckMain : Application() {
    override fun start(primaryStage: Stage) {
        val fxmlLoader = FXMLLoader(NVLCheckMain::class.java.getResource("NVLCheck.fxml"))
        val scene = Scene(fxmlLoader.load(), 800.0, 600.0)
        primaryStage.title = "NVL Config Check"
        primaryStage.scene = scene
        primaryStage.show()
    }
}


fun main() {
    Application.launch(NVLCheckMain::class.java)
}