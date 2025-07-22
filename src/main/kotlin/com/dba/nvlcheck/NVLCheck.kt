package com.dba.nvlcheck

import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.PropertyValueFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javafx.stage.FileChooser
import javafx.stage.Window
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.IOException

data class ValueList(val item: String?, val quantity: String?, val sku: String?)

class NVLCheck {

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
    @FXML
    lateinit var sourceFile: File
    @FXML
    lateinit var targetFile: File
    val logger: Logger = LoggerFactory.getLogger("Excel Reader")

    fun openSourceFileChooser(ownerWindow: Window?, directory: String): File {
        val fileChooser = FileChooser()
        fileChooser.title = "Open Configurator ExcelFile"


        fileChooser.initialDirectory = File(directory)

        fileChooser.extensionFilters.addAll(
            FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        )

        val selectedFile: File = fileChooser.showOpenDialog(ownerWindow)

        return selectedFile

    }

    @FXML
    fun handleOpenSourceFile() {
        val configDirectory =
        "C:\\Users\\albertd\\OneDrive - Hewlett Packard Enterprise\\HPE\\NVL72"
        val ownerWindow = labelJavaFX.scene?.window
        sourceFile = openSourceFileChooser(ownerWindow, configDirectory)
        logger.info("Selected file:  ${sourceFile.absolutePath}")
        labelSourceFile.text = sourceFile.absolutePath
    }

    @FXML
    fun handleOpenTargetFile() {
        val targetDirectory =
            "C:\\Users\\albertd\\Downloads"
        val ownerWindow = labelJavaFX.scene?.window
        targetFile = openSourceFileChooser(ownerWindow, targetDirectory)
        logger.info("Selected file:  ${targetFile.absolutePath}")
        labelTargetFile.text = targetFile.absolutePath
    }

    @FXML
    fun handleCompare() {
        val configValueList = mutableListOf<ValueList>()
        val targetValueList = mutableListOf<ValueList>()
        val configFirstRow = 3
        val configLastRow = 42
        var itemValue: String?
        var qtyValue: String? = null
        var skuValue: String?

        try {
            FileInputStream(sourceFile).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet: XSSFSheet = workbook.getSheet("BoM")
                    val evaluator: FormulaEvaluator = workbook.creationHelper.createFormulaEvaluator()

                    for (i in configFirstRow..configLastRow) {
                        val row = sheet.getRow(i)
                        if (row == null)
                            continue
                        val itemCell: XSSFCell? = row.getCell(5)
                        evaluator.evaluateInCell(itemCell)
                        itemValue = itemCell?.stringCellValue

                        val qtyCell: XSSFCell? = row.getCell(6)
                        evaluator.evaluateInCell(qtyCell)
                        if (qtyCell?.cellType == CellType.NUMERIC) {
                            qtyValue = qtyCell.numericCellValue.toString()
                        } else if (qtyCell?.cellType == CellType.STRING) {
                            continue
                        }
                        val skuCell: XSSFCell? = row.getCell(7)
                        evaluator.evaluateInCell(skuCell)
                        skuValue = skuCell?.stringCellValue

                        configValueList.add(ValueList(itemValue, qtyValue, skuValue))
                    }
                }
            }
        } catch (e: IOException) {
            logger.error("Error reading config file $sourceFile", e)
        }

        logger.info("Data read from config file")

        val targetFirstRow = 6
        val targetLastRow = 43
        try {
            FileInputStream(targetFile).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet: XSSFSheet = workbook.getSheet("ExpertBOM")

                    for (i in targetFirstRow..targetLastRow) {
                        val row = sheet.getRow(i)
                        if (row == null)
                            continue
                        val itemCell: XSSFCell? = row.getCell(0)
                        itemValue = itemCell?.stringCellValue

                        val qtyCell: XSSFCell?  = row.getCell(1)
                        if (qtyCell == null)
                            continue
                        if (qtyCell.cellType == CellType.NUMERIC) {
                            qtyValue = qtyCell.numericCellValue.toString()
                        } else if (qtyCell.cellType == CellType.STRING || qtyCell.cellType == CellType.BLANK) {
                            continue
                        }

                        val skuCell: XSSFCell?  = row.getCell(2)
                        skuValue = skuCell?.stringCellValue
                        targetValueList.add(ValueList(itemValue, qtyValue, skuValue))
                    }
                    fis.close()
                }
            }

        } catch (e: IOException) {
            logger.error("Error reading target file $targetFile", e)
        }

        logger.info("Data read from target file")

        val configSet = configValueList.toSet()
        val targetSet = targetValueList.toSet()

        val allValuesMatch = configSet == targetSet

        tableDiff.items.clear()


        if (allValuesMatch) {
            labelResult.text = "All items match"
        } else {
            labelResult.text = "Files do not match"
            val diffSet = configSet - targetSet
            tableDiff.items.addAll(diffSet)
            tableDiff.isVisible = true
        }

    }
    @FXML
    fun handleQuit() {
        Platform.exit()
    }

    @FXML
    fun initialize() {
        labelJDK.text = "Java SDK version %s".format(Runtime.version().toString())
        labelJavaFX.text = "JavaFX version %s".format(System.getProperties().get("javafx.runtime.version"))

        logger.info("Start application")

        val itemCol = TableColumn<ValueList, String>("Item")
        itemCol.cellValueFactory = PropertyValueFactory<ValueList, String>("item")

        val quantityCol = TableColumn<ValueList, String>("Quantity")
        quantityCol.cellValueFactory = PropertyValueFactory<ValueList, String>("quantity")

        val skuCol = TableColumn<ValueList, String>("SKU")
        skuCol.cellValueFactory = PropertyValueFactory<ValueList, String>("sku")

        tableDiff.isVisible = false
        tableDiff.columns.setAll(quantityCol, skuCol)

    }
}