module org.dba.nvlcheck {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;
    requires org.slf4j;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires java.prefs;


    opens org.dba.nvlcheck to javafx.fxml;
    exports org.dba.nvlcheck;
}