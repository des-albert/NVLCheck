package com.dba.nvlcheck

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.concurrent.Task
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javafx.stage.FileChooser
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.prefs.Preferences

data class ValueList(val item: String?, val quantity: String?, val sku: String?)

class NVLCheck {

    @FXML
    lateinit var skuCol: TableColumn<ValueList, String>

    @FXML
    lateinit var quantityCol: TableColumn<ValueList, String>

    @FXML
    lateinit var itemCol: TableColumn<ValueList, String>

    @FXML
    lateinit var buttonQuit: Button

    @FXML
    lateinit var tableDiff: TableView<ValueList>

    @FXML
    lateinit var labelResult: Label

    @FXML
    lateinit var labelTargetFile: Label

    @FXML
    lateinit var buttonTarget: Button

    @FXML
    lateinit var buttonCompare: Button

    @FXML
    lateinit var labelSourceFile: Label

    @FXML
    lateinit var buttonSource: Button

    @FXML
    lateinit var labelJavaFX: Label

    @FXML
    lateinit var labelJDK: Label


    private lateinit var sourceFile: File
    private lateinit var targetFile: File
    private val logger: Logger = LoggerFactory.getLogger("Excel Reader")
    private val dataFormatter = DataFormatter()
    private val prefs: Preferences = Preferences.userNodeForPackage(NVLCheck::class.java)
    private val lastSourceDirKey = "lastSourceDir"
    private val lastTargetDirKey = "lastTargetDir"

    @FXML
    fun handleOpenSourceFile() {
        val initialDir = prefs.get(lastSourceDirKey, System.getProperty("user.home"))
        openFileChooser(initialDir)?.let { file ->
            sourceFile = file
            labelSourceFile.text = file.name
            prefs.put(lastSourceDirKey, file.parent) // Save the new directory
        }
    }
    @FXML
    fun handleOpenTargetFile() {
        val initialDir = prefs.get(lastTargetDirKey, System.getProperty("user.home"))
        openFileChooser(initialDir)?.let { file ->
            targetFile = file
            labelTargetFile.text = file.name
            prefs.put(lastTargetDirKey, file.parent) // Save the new directory
        }
    }

    private fun openFileChooser(directoryPath: String): File? {
        val fileChooser = FileChooser().apply {
            title = "Open Excel File"
            initialDirectory = File(directoryPath)
            extensionFilters.add(FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx"))
        }
        return fileChooser.showOpenDialog(labelJavaFX.scene.window)
    }

    @FXML
    fun handleCompare() {
        if (!::sourceFile.isInitialized || !::targetFile.isInitialized) {
            labelResult.text = "Please select both source and target files."
            return
        }

        // IMPROVEMENT: Perform file processing on a background thread using a Task.
        val compareTask = object : Task<Pair<Boolean, Set<ValueList>>>() {
            override fun call(): Pair<Boolean, Set<ValueList>> {
                updateMessage("Reading source file: ${sourceFile.name}...")
                val configValues = readValuesFromFile(sourceFile, "BoM", 3, 42, 5, 6, 7)

                updateMessage("Reading target file: ${targetFile.name}...")
                val targetValues = readValuesFromFile(targetFile, "ExpertBOM", 6, 43, 0, 1, 2)

                updateMessage("Comparing values...")
                val configSet = configValues.toSet()
                val targetSet = targetValues.toSet()

                val allMatch = configSet == targetSet
                val differences = configSet - targetSet

                return Pair(allMatch, differences)
            }
        }

        // Bind UI elements to the task's state for real-time feedback.
        bindUIToTask(compareTask)

        Thread(compareTask).start()
    }

    private fun readValuesFromFile(file: File, sheetName: String, firstRow: Int, lastRow: Int, itemCol: Int, qtyCol: Int, skuCol: Int): List<ValueList> {
        val values = mutableListOf<ValueList>()
        try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val evaluator = workbook.creationHelper.createFormulaEvaluator()

                    val sheet = workbook.getSheet(sheetName) ?: run {
                        logger.error("Sheet '$sheetName' not found in ${file.name}")
                        throw IOException("Sheet '$sheetName' not found in ${file.name}")
                    }

                    for (i in firstRow..lastRow) {
                        val row = sheet.getRow(i) ?: continue

                        val itemValue = dataFormatter.formatCellValue(row.getCell(itemCol),evaluator).trim()
                        if (itemValue.isBlank() || itemValue == "Total") continue

                        val qtyValue = dataFormatter.formatCellValue(row.getCell(qtyCol), evaluator).trim()
                        val skuValue = dataFormatter.formatCellValue(row.getCell(skuCol), evaluator).trim()

                        values.add(ValueList(itemValue, qtyValue, skuValue))
                    }
                }
            }
        } catch (e: IOException) {
            logger.error("Error reading file ${file.name}", e)
            throw e
        }
        return values
    }

    private fun bindUIToTask(task: Task<Pair<Boolean, Set<ValueList>>>) {
        // Provide feedback while the task is running.

        labelResult.textProperty().bind(task.messageProperty())
        buttonCompare.disableProperty().bind(task.runningProperty())
        buttonSource.disableProperty().bind(task.runningProperty())
        buttonTarget.disableProperty().bind(task.runningProperty())

        task.setOnSucceeded {
            unbindUIFromTask()
            val (allMatch, differences) = task.value
            if (allMatch) {
                labelResult.text = "Success: All items match."
                tableDiff.isVisible = false
            } else {
                labelResult.text = "Mismatch found. See differences below."
                tableDiff.items = FXCollections.observableArrayList(differences)
                tableDiff.isVisible = true
            }
        }

        task.setOnFailed {
            unbindUIFromTask()
            labelResult.text = "An error occurred during comparison. See logs for details."
            logger.error("Comparison task failed", task.exception)
        }
    }


    private fun unbindUIFromTask() {
        labelResult.textProperty().unbind()
        buttonCompare.disableProperty().unbind()
        buttonSource.disableProperty().unbind()
        buttonTarget.disableProperty().unbind()
    }

    @FXML
    fun handleQuit() {
        Platform.exit()
    }

    @FXML
    fun initialize() {
        labelJDK.text = "Java SDK version: ${Runtime.version()}"
        labelJavaFX.text = "JavaFX version: ${System.getProperty("javafx.runtime.version")}"
        logger.info("Application started.")

        itemCol.cellValueFactory = PropertyValueFactory("item")
        quantityCol.cellValueFactory = PropertyValueFactory("quantity")
        skuCol.cellValueFactory = PropertyValueFactory("sku")

        tableDiff.isVisible = false
    }
}