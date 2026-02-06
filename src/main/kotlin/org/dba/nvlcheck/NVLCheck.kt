package org.dba.nvlcheck

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.ToggleGroup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.prefs.Preferences
import javafx.stage.FileChooser
import javafx.concurrent.Task
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.text.Font
import java.io.IOException


data class ValueList(
    val item: String?,
    val quantity: String?,
    val sku: String?,
    val solID: String?
)

// Define a class to hold the parsing configuration for a file type

data class FileParseConfig(
    val sheetName: String,
    val firstRow: Int,
    val itemCol: Int,
    val qtyCol: Int,
    val skuCol: Int,
    val solIDCol: Int
)

class NVLCheck {

    @FXML
    lateinit var radio72GB200: RadioButton

    @FXML
    lateinit var radio4GB200: RadioButton

    @FXML
    lateinit var radio72GB300: RadioButton

    @FXML
    lateinit var radio72VR200: RadioButton

    @FXML
    lateinit var confGroup: ToggleGroup


    @FXML
    lateinit var solIDCol: TableColumn<ValueList, String>

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
    lateinit var labelJavaFX: Label

    @FXML
    lateinit var labelJDK: Label

    private val NVL72_GB300 = "NVL72 GB300 Configurator v14.0.xlsx"
    private val NVL72_GB200 = "NVL72 GB200 Configurator v5.0.xlsx"
    private val NVL4_GB200 = "NVL4 GB200 Configurator v7.0.xlsx"
    private val NVL72_VR200 = "NVL72 VR200 Configurator v2.0.xlsx"

    private val configRootPath = "C:\\Users\\albertd\\OneDrive - Hewlett Packard Enterprise\\HPE\\NVL\\"
    private var sourcePath = configRootPath + NVL72_GB300
    private var sourceFile = File(sourcePath)
    private lateinit var targetFile: File
    private val logger: Logger = LoggerFactory.getLogger("Excel Reader")
    private val dataFormatter = DataFormatter()
    private val prefs: Preferences = Preferences.userNodeForPackage(NVLCheck::class.java)
    private val lastTargetDirKey = "lastTargetDir"
    private val sourceFileConfig = FileParseConfig(
        sheetName = "BoM",
        firstRow = 3,
        itemCol = 5,
        qtyCol = 6,
        skuCol = 7,
        solIDCol = 9
    )
    private val targetFileConfig = FileParseConfig(
        sheetName = "ExpertBOM",
        firstRow = 6,
        itemCol = 0,
        qtyCol = 1,
        skuCol = 2,
        solIDCol = 5
    )


    @FXML
    fun sourceSelect() {
        var selectedSource = ""
        val selectedToggle = confGroup.selectedToggle
        if (selectedToggle != null) {
            val selectedRadio = selectedToggle as RadioButton
            selectedSource = selectedRadio.text
        }

        when (selectedSource) {
            "NVL72 GB300" -> {
                sourcePath = configRootPath + NVL72_GB300
            }
            "NVL72 GB200" -> {
                sourcePath = configRootPath + NVL72_GB200
            }
            "NVL4 GB200" -> {
                sourcePath = configRootPath + NVL4_GB200
            }
            "NVL72 VR200" -> {
                sourcePath = configRootPath + NVL72_VR200
            }
        }
        sourceFile = File(sourcePath)
        labelSourceFile.text = sourceFile.name
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
        if ( !::targetFile.isInitialized) {
            labelResult.text = "Please select target file."
            return
        }

        // Perform file processing on a background thread using a Task.

        val compareTask = object : Task<Pair<Boolean, Set<ValueList>>>() {
            override fun call(): Pair<Boolean, Set<ValueList>> {
                updateMessage("Reading source file: ${sourceFile.name}...")
                val configValues = readValuesFromFile(sourceFile, sourceFileConfig)

                updateMessage("Reading target file: ${targetFile.name}...")
                val targetValues = readValuesFromFile(targetFile, targetFileConfig)

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

    private fun readValuesFromFile(file: File, config: FileParseConfig): List<ValueList> {
        val values = mutableListOf<ValueList>()
        try {
            WorkbookFactory.create(file, null, true).use { workbook ->
                val evaluator = workbook.creationHelper.createFormulaEvaluator()

                val sheet = workbook.getSheet(config.sheetName) ?: run {
                    logger.error("Sheet '${config.sheetName}' not found in ${file.name}")
                    throw IOException("Sheet '${config.sheetName}' not found in ${file.name}")
                }

                for (i in config.firstRow..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue

                    val itemValue = dataFormatter.formatCellValue(row.getCell(config.itemCol), evaluator).trim()
                    if (itemValue.isBlank() || itemValue.equals("Total", ignoreCase = true)) continue

                    val qtyValue = dataFormatter.formatCellValue(row.getCell(config.qtyCol), evaluator).trim()
                    val skuValue = dataFormatter.formatCellValue(row.getCell(config.skuCol), evaluator).trim()
                    val solIDValue = dataFormatter.formatCellValue(row.getCell(config.solIDCol), evaluator).trim()

                    values.add(ValueList(itemValue, qtyValue, skuValue, solIDValue))
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
        buttonTarget.disableProperty().bind(task.runningProperty())

        task.setOnSucceeded {
            unbindUIFromTask()
            val (allMatch, differences) = task.value
            if (allMatch) {
                labelResult.text = "Success: All items match."
                labelResult.font = Font.font(24.0)
                labelResult.textFill = javafx.scene.paint.Color.GREEN
                tableDiff.isVisible = false
            } else {
                labelResult.text = "Mismatch found. See differences below."
                labelResult.font = Font.font(24.0)
                labelResult.textFill = javafx.scene.paint.Color.RED
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
        solIDCol.cellValueFactory = PropertyValueFactory("solID")

        labelSourceFile.text = sourceFile.name

        tableDiff.isVisible = false
    }
}
  
