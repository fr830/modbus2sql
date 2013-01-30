//////////////////////////////////////////////////////////////////////////
//com.enrogen.modbus2sql.MainWindow
//2010 - James A R Brown
//Released under GPL V2
//////////////////////////////////////////////////////////////////////////
package com.enrogen.modbus2sql;

import org.jdesktop.application.Action;
import org.jdesktop.application.SingleFrameApplication;
import org.jdesktop.application.FrameView;
import java.io.File;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import com.enrogen.modbus2sql.MainWindow.SpinnerEditor;
import com.enrogen.ModbusConnection.*;
import com.enrogen.sql.*;
import com.enrogen.xml.*;
import com.enrogen.CustomRenderer.*;
import com.enrogen.intHandler.bit16IntHandler;
import com.enrogen.intHandler.bit32IntHandler;
import com.enrogen.intHandler.int16to16binary;
import com.enrogen.intHandler.int32to16binary;
import com.enrogen.logger.*;
import gnu.io.CommPortIdentifier;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

public class MainWindow extends FrameView implements modbus2sql {

    public MainWindow(SingleFrameApplication app) {
        //NB Generated component init
        super(app);
        initComponents();

        //Makesure .modbus2sql is created
        checkHomeDir();

        //Set the log textarea to a fixed font
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);
        text_messages.setFont(monoFont);

        //Startup the logging
        startEgLogger();
        addMessage("Modbus2SQL Startup");

        //Read the XML Settings
        boolean startupOK = firstStart();

        //If startup start the modules
        if (startupOK) {
        } else {
            System.exit(0);
        }

        //Debug the Jamod Library (provides modbus echo)
        if (checkDebugJamod.isSelected()) {
            System.setProperty("net.wimpi.modbus.debug", "true");
        } else {
            System.setProperty("net.wimpi.modbus.debug", "false");
        }

        //Debug the Enrogen SQL Library
        if (checkDebugSQL.isSelected()) {
            System.setProperty("com.enrogen.sql.debug", "true");
        } else {
            System.setProperty("com.enrogen.sql.debug", "false");
        }

        //Debug the Enrogen ModbusConnection Library
        if (checkDebugModbusConn.isSelected()) {
            System.setProperty("com.enrogen.ModbusConnection.debug", "true");
            addMessage("com.enrogen.ModbusConnection.debug - Debugging On");
        } else {
            System.setProperty("com.enrogen.ModbusConnection.debug", "false");
            addMessage("com.enrogen.ModbusConnection.debug - Debugging Off");
        }

        //Start the SQL Connection
        StartSQL();
        StartSQLTicker();

        //Start the TCP Port
        OpenTCPPort();
        startTCPThread();

        //Add a listener to deal with resizing
        addListenerLayeredPane();

        //Disable the sql edit boxes
        finishSQLSetting();

        //Start Modbus conn
        if (checkStartModbusAuto.isSelected()) {
            startModbusFlag = true;
        }


    }
    //////////////////////////////////////////////////////////////////////////
    //Messages (Text Box in GUI)
    //////////////////////////////////////////////////////////////////////////
    private int lineCounter = 1;

    private void addMessage(String Message) {
        Logger.log(Level.INFO, Message);
    }

    @Action
    public void clearTextArea() {
        text_messages.setText("");
    }
    //////////////////////////////////////////////////////////////////////////
    //Logging
    //////////////////////////////////////////////////////////////////////////
    private Logger Logger;

    private void startEgLogger() {
        EgLogger EgLogger = new EgLogger();
        EgLogger.setFileHandler(LOG_MODBUS2SQL_FILENAME, LOG_SIZE_LIMIT, LOG_MAX_FILES);
        EgLogger.setWindowHandler(text_messages);
        EgLogger.initEgLogger();
        Logger = EgLogger.getLogger();
    }
    //////////////////////////////////////////////////////////////////////////
    //System Tickers
    //////////////////////////////////////////////////////////////////////////
    private javax.swing.Timer sqlTimer = null;
    private javax.swing.Timer modbusTimer = null;
    private javax.swing.Timer watchdogTimer = null;
    private javax.swing.Timer autoRefreshTimer = null;

    private void StartSQLTicker() {
        if (sqlTimer == null) {
            //Create a thread to flash the lamps
            int period = modbus2sql.SQL_ALIVE_POLL_TICKER;
            addMessage("Starting SQL Keep Alive Ticker at : " + period + "mSec");
            ActionListener taskPerformer = new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    flashLamps();
                }
            };
            sqlTimer = new Timer(period, taskPerformer);
            sqlTimer.setInitialDelay(0);
        }
        sqlTimer.start();
    }

    private void StartModbusTicker() {
        if (modbusTimer == null) {
            int period = modbus2sql.MODBUS_POLL_TICKER;
            addMessage("Starting Modbus Polling Ticker at : " + period + "mSec");
            ActionListener taskPerformer = new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    ModbusLoop();
                }
            };
            modbusTimer = new Timer(period, taskPerformer);
            modbusTimer.setInitialDelay(250);
        }
        //modbusTimer.start();
    }

    private void StartWatchDogTicker() {
        if (watchdogTimer == null) {
            int period = modbus2sql.WATCHDOG_POLL_TICKER;
            addMessage("Starting Watchdog Polling Ticker at : " + period + "mSec");
            ActionListener taskPerformer = new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    watchdog();
                }
            };
            watchdogTimer = new Timer(period, taskPerformer);
            watchdogTimer.setInitialDelay(500);
        }
        watchdogTimer.start();

    }

    private void StartAutoRefreshTicker() {
        if (autoRefreshTimer == null) {
            int period = 3000; //msec
            addMessage("Starting Auto Refresh Ticker at : " + period + "mSec");
            ActionListener taskPerformer = new ActionListener() {

                public void actionPerformed(ActionEvent evt) {
                    refreshViewerTable();
                }
            };
            autoRefreshTimer = new Timer(period, taskPerformer);
            autoRefreshTimer.setInitialDelay(0);
        }
        autoRefreshTimer.start();
    }
    //////////////////////////////////////////////////////////////////////////
    //Start and Stop Manual Control
    //////////////////////////////////////////////////////////////////////////
    private boolean startModbusFlag = false;

    @Action
    public void btnStart() {
        try {
            addMessage("Starting Modbus Looper");
            modbusTimer.start();
        } catch (Exception e) {
            addMessage("There was a problem trying to start the Modbus Connection Timer");
            e.printStackTrace();
        }
    }

    @Action
    public void btnStop() {
        try {
            modbusTimer.stop();
            closeModbusConnection();
        } catch (Exception e) {
        }
    }
    //////////////////////////////////////////////////////////////////////////
    //MySQL Tab
    //////////////////////////////////////////////////////////////////////////
    private SQLCommand SQLConnection = new SQLCommand();

    //Start and if necessary restart sequence
    private void StartSQL() {
        //Get SQL parameters
        String sqlServerIP = text_sqlserverip.getText();
        String sqlUsername = text_sqlusername.getText();
        String sqlPassword = text_sqlpassword.getText();
        String sqlDatabaseName = text_sqldatabasename.getText();

        //Try to clean up the broken connection
        SQLConnection.closeSQLConnection();

        //Open the SQL Connection
        SQLConnection.setSQLParams(sqlServerIP, sqlUsername, sqlPassword, sqlDatabaseName);
        addMessage("Starting SQL Connection");
        SQLConnection.restartSQLConnection();
        addMessage("Starting SQL Keep Alive");
        SQLConnection.StartKeepAlive();
    }

    private void flashLamps() {
        if (SQLConnection.isAlive()) {
            //Start Things
            redlamp.setEnabled(false);
            mainTabbedPanel.setEnabledAt(1, true);

            //Flash Lamps
            if (greenlamp.isEnabled()) {
                greenlamp.setEnabled(false);
            } else {
                greenlamp.setEnabled(true);
            }

            //if sql was down and is now up
            if (SQLConnection.isRestarted()) {
                StartModbusTicker();
                StartWatchDogTicker();
                SQLConnection.resetRestartedFlag();
                refreshSetupTab();

                //Initial startup flag
                if (startModbusFlag == true) {
                    startModbusFlag = false;
                    btnStart();
                }
            }
        } else {
            //Stop Things
            greenlamp.setEnabled(false);
            mainTabbedPanel.setEnabledAt(1, false);
            try {
                modbusTimer.stop();
                watchdogTimer.stop();
            } catch (Exception e) {
            }
            //Flash lamps
            if (redlamp.isEnabled()) {
                redlamp.setEnabled(false);
            } else {
                redlamp.setEnabled(true);
            }
        }
    }

    @Action
    public void editSQLSettings() {
        text_sqlserverip.setEditable(true);
        text_sqlserverport.setEditable(true);
        text_sqlusername.setEditable(true);
        text_sqlpassword.setEditable(true);
        text_sqldatabasename.setEditable(true);
        btn_sqlsave.setEnabled(true);
        btn_sqlcancel.setEnabled(true);
        btn_sqledit.setEnabled(false);
    }

    @Action
    public void cancelSQLSettings() {
        readSQLSettings();
        finishSQLSetting();
    }

    public void readSQLSettings() {
        //Read the xml file
        String SettingsXMLFile = modbus2sql.FULL_SETTING_XML_PATH;
        xmlIO.setFileName(SettingsXMLFile);
        xmlIO.parseXmlFile();

        //Get the SQL Setting into the boxes
        text_messages.setText(text_messages.getText().toString() + "\nReading setting.xml");
        String sqlServerIP = xmlIO.readXmlTagValue("mysql", "ServerIP");
        String sqlPort = xmlIO.readXmlTagValue("mysql", "Port");
        String sqlUsername = xmlIO.readXmlTagValue("mysql", "Username");
        String sqlPassword = xmlIO.readXmlTagValue("mysql", "Password");
        String sqlDatabaseName = xmlIO.readXmlTagValue("mysql", "DatabaseName");

        //Update the text boxes
        text_sqlserverip.setText(sqlServerIP);
        text_sqlserverport.setText(sqlPort);
        text_sqlusername.setText(sqlUsername);
        text_sqlpassword.setText(sqlPassword);
        text_sqldatabasename.setText(sqlDatabaseName);
    }

    public void finishSQLSetting() {
        text_sqlserverip.setEditable(false);
        text_sqlserverport.setEditable(false);
        text_sqlusername.setEditable(false);
        text_sqlpassword.setEditable(false);
        text_sqldatabasename.setEditable(false);
        btn_sqlsave.setEnabled(false);
        btn_sqlcancel.setEnabled(false);
        btn_sqledit.setEnabled(true);
    }

    //////////////////////////////////////////////////////////////////////////
    //New dialogs
    //////////////////////////////////////////////////////////////////////////
    @Action
    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            aboutBox = new AboutBoxWindow(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        Modbus2SQLApp.getApplication().show(aboutBox);
    }
    ////////////////////////////////////////////////////////////////////////////
    //First Startup and XML Access
    ////////////////////////////////////////////////////////////////////////////
    private xmlio xmlIO = null;

    private void checkHomeDir() {
        //Check we have a default directory created and if not create it
        String modbus2sqlDir = modbus2sql.FULL_MODBUS2SQL_PATH;
        boolean exists = (new File(modbus2sqlDir)).exists();
        if (!exists) {
            System.out.println("No Home directory found");
            System.out.println("Creating : " + modbus2sqlDir);
            boolean success = (new File(modbus2sql.FULL_MODBUS2SQL_PATH)).mkdir();
        }
    }

    @Action
    public boolean firstStart() {
        xmlIO = new xmlio();

        String modbus2sqlDir = modbus2sql.FULL_MODBUS2SQL_PATH;
        boolean exists = (new File(modbus2sqlDir)).exists();

        //Create if necessary the XML File
        String SettingsXMLFile = modbus2sql.FULL_SETTING_XML_PATH;
        exists = (new File(SettingsXMLFile)).exists();
        if (!exists) {
            addMessage("No setting.xml File");
            addMessage("Creating Default: " + SettingsXMLFile);

            xmlIO.createNewXmlFile(SettingsXMLFile);
            xmlIO.addRootNode("Modbus2SQL");
            xmlIO.addSubNode("Modbus2SQL", "default");
            xmlIO.addSubNode("default", "XMLversion", String.valueOf(modbus2sql.REQUIRED_XML_VERSION));
            xmlIO.addSubNode("Modbus2SQL", "mysql");
            xmlIO.addSubNode("mysql", "ServerIP", modbus2sql.MYSQL_DEFAULT_SERVER);
            xmlIO.addSubNode("mysql", "Port", modbus2sql.MYSQL_DEFAULT_PORT);
            xmlIO.addSubNode("mysql", "Username", modbus2sql.MYSQL_DEFAULT_USER);
            xmlIO.addSubNode("mysql", "Password", modbus2sql.MYSQL_DEFAULT_PASSWORD);
            xmlIO.addSubNode("mysql", "DatabaseName", modbus2sql.MYSQL_DEFAULT_DATABASE);
            xmlIO.addSubNode("Modbus2SQL", "Startup");
            xmlIO.addSubNode("Startup", "ModbusAutoStart", "false");
            xmlIO.addSubNode("Modbus2SQL", "Debug");
            xmlIO.addSubNode("Debug", "com.enrogen.sql", "false");
            xmlIO.addSubNode("Debug", "com.enrogen.ModbusConnection", "false");
            xmlIO.addSubNode("Debug", "net.wimpi.jamod", "false");

            boolean success = xmlIO.writeXMLFile();
            if (!success) {
                addMessage("Error Creating setting.xml File");
            }
        }

        addMessage("Reading setting.xml");
        xmlIO.setFileName(SettingsXMLFile);
        xmlIO.parseXmlFile();

        addMessage("Checking setting.xml version");
        String value = xmlIO.readXmlTagValue("default", "XMLversion");
        if (Double.parseDouble(value) < modbus2sql.REQUIRED_XML_VERSION) {
            addMessage("The XML Setting file setting.xml is incorrect version");
            return false;
        }

        //text_messages.setText(text_messages.getText().toString() + "\nReading setting.xml");
        String sqlServerIP = xmlIO.readXmlTagValue("mysql", "ServerIP");
        String sqlPort = xmlIO.readXmlTagValue("mysql", "Port");
        String sqlUsername = xmlIO.readXmlTagValue("mysql", "Username");
        String sqlPassword = xmlIO.readXmlTagValue("mysql", "Password");
        String sqlDatabaseName = xmlIO.readXmlTagValue("mysql", "DatabaseName");

        //Update the text boxes
        text_sqlserverip.setText(sqlServerIP);
        text_sqlserverport.setText(sqlPort);
        text_sqlusername.setText(sqlUsername);
        text_sqlpassword.setText(sqlPassword);
        text_sqldatabasename.setText(sqlDatabaseName);

        //Get the checkboxes
        boolean BoolcheckStartModbusAuto = Boolean.valueOf((String) xmlIO.readXmlTagValue("Startup", "ModbusAutoStart"));
        boolean BoolcheckDebugJamod = Boolean.valueOf((String) xmlIO.readXmlTagValue("Debug", "net.wimpi.jamod"));
        boolean BoolcheckDebugModbusConn = Boolean.valueOf((String) xmlIO.readXmlTagValue("Debug", "com.enrogen.ModbusConnection"));
        boolean BoolcheckDebugSQL = Boolean.valueOf((String) xmlIO.readXmlTagValue("Debug", "com.enrogen.sql"));

        //Apply the values
        checkStartModbusAuto.setSelected(BoolcheckStartModbusAuto);
        checkDebugJamod.setSelected(BoolcheckDebugJamod);
        checkDebugModbusConn.setSelected(BoolcheckDebugModbusConn);
        checkDebugSQL.setSelected(BoolcheckDebugSQL);

        return true;
    }

    @Action
    public boolean saveSettingXML() {
        addMessage("Saving setting.xml");
        boolean sucess = false;

        //SQL Settings
        String username = text_sqlusername.getText();
        String password = text_sqlpassword.getText();
        String serverip = text_sqlserverip.getText();
        String serverport = text_sqlserverport.getText();
        String dbname = text_sqldatabasename.getText();

        //Others
        String BoolcheckStartModbusAuto = String.valueOf(checkStartModbusAuto.isSelected());
        String BoolcheckDebugJaMod = String.valueOf(checkDebugJamod.isSelected());
        String BoolcheckDebugModbusConn = String.valueOf(checkDebugModbusConn.isSelected());
        String BoolcheckDebugSQL = String.valueOf(checkDebugSQL.isSelected());

        String SettingsXMLFile = modbus2sql.FULL_SETTING_XML_PATH;

        xmlIO.createNewXmlFile(SettingsXMLFile);
        xmlIO.addRootNode("Modbus2SQL");
        xmlIO.addSubNode("Modbus2SQL", "default");
        xmlIO.addSubNode("default", "XMLversion", String.valueOf(modbus2sql.REQUIRED_XML_VERSION));
        xmlIO.addSubNode("Modbus2SQL", "mysql");
        xmlIO.addSubNode("mysql", "ServerIP", serverip);
        xmlIO.addSubNode("mysql", "Port", serverport);
        xmlIO.addSubNode("mysql", "Username", username);
        xmlIO.addSubNode("mysql", "Password", password);
        xmlIO.addSubNode("mysql", "DatabaseName", dbname);
        xmlIO.addSubNode("Modbus2SQL", "Startup");
        xmlIO.addSubNode("Startup", "ModbusAutoStart", BoolcheckStartModbusAuto);
        xmlIO.addSubNode("Modbus2SQL", "Debug");
        xmlIO.addSubNode("Debug", "com.enrogen.sql", BoolcheckDebugSQL);
        xmlIO.addSubNode("Debug", "com.enrogen.ModbusConnection", BoolcheckDebugModbusConn);
        xmlIO.addSubNode("Debug", "net.wimpi.jamod", BoolcheckDebugJaMod);

        sucess = xmlIO.writeXMLFile();

        //If ok start the sql with new settings
        if (sucess) {
            addMessage("Sucess : Save setting.xml");
            StartSQL();
        } else {
            addMessage("FAILED : Save setting.xml");
        }

        //This resets the buttons to non-editable
        finishSQLSetting();

        return sucess;
    }

    private class SaveSettingXMLTask extends org.jdesktop.application.Task<Object, Void> {

        SaveSettingXMLTask(org.jdesktop.application.Application app) {
            // Runs on the EDT.  Copy GUI state that
            // doInBackground() depends on from parameters
            // to SaveSettingXMLTask fields, here.
            super(app);
        }

        @Override
        protected Object doInBackground() {
            // Your Task's code here.  This method runs
            // on a background thread, so don't reference
            // the Swing GUI from here.
            return null;  // return your result
        }

        @Override
        protected void succeeded(Object result) {
            // Runs on the EDT.  Update the GUI based on
            // the result computed by doInBackground().
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //Setup Tab - Painting
    ////////////////////////////////////////////////////////////////////////////
    public void refreshSetupTab() {
        if (SQLConnection.isAlive()) {
            addMessage("Refreshing GUI Setup Tab");
            refreshTypesTable();
            refreshSlaveTable();
            refreshPagesTable();
            refreshRS485Page();
            fillViewerComboBox();
            refreshAlarmsMonitoredTable();
            refreshAlarmsFlagTable();
            refreshAlarmAnnunciatortable();
        }
        return;
    }

    ///////////////////////////////////////////////////////////////////////////
    //JLayeredPane Drawing
    ///////////////////////////////////////////////////////////////////////////
    public void addListenerLayeredPane() {
        mainPanel.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent evt) {
                reSizeAll();
            }
        });
    }

    public void reSizeAll() {
        Rectangle r = mainPanel.getBounds();

        //Shrink it a bit
        r.grow(0, -10);
        mainTabbedPanel.setBounds(r);
        mainTabbedPanel.repaint();

        //Repaint the children
        mysqlPanel.repaint();
    }

    ////////////////////////////////////////////////////////////////////////////
    //Setup Tab - Controller Types
    ////////////////////////////////////////////////////////////////////////////
    public void refreshTypesTable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, controllertype, longname FROM controllertypes";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Unique ID", "Short Name", "Long Name"};
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        //Delete any existing rows
        if (rows > 0) {
            for (int count = rows; count == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_controllertypes.setModel(aModel);
    }

    @Action
    public void applychangesToControllerTypes() {
        DefaultTableModel aModel = (DefaultTableModel) table_controllertypes.getModel();
        int rowCount = aModel.getRowCount();
        addMessage("Updating Controller Types to database");

        for (int i = 0; i < rowCount; i++) {
            Integer rowid = (Integer) aModel.getValueAt(i, 0);
            String controllertype = (String) aModel.getValueAt(i, 1);
            String longname = (String) aModel.getValueAt(i, 2);

            //Get previous value
            String sqlPreviousValue = "SELECT controllertype FROM controllertypes WHERE rowid=" + rowid + ";";
            String previousValue = "";
            List resultList = SQLConnection.SQLSelectCommand(sqlPreviousValue);
            List resultValues = (List) resultList.get(0);
            previousValue = (String) resultValues.get(0);

            //Now update tables
            String sqlcmd = "UPDATE controllertypes SET controllertype='" + controllertype
                    + "', " + "longname='" + longname + "' "
                    + "WHERE rowid=" + rowid + ";";

            String sqlcmd2 = "UPDATE slaves SET controllertype='" + controllertype
                    + "' WHERE controllertype='" + previousValue + "';";

            String sqlcmd3 = "UPDATE registerblocks SET controllertype='" + controllertype
                    + "' WHERE controllertype='" + previousValue + "';";

            String sqlcmd4 = "UPDATE registerdetail SET controllertype='" + controllertype
                    + "' WHERE controllertype='" + previousValue + "';";

            SQLConnection.SQLUpdateCommand(sqlcmd);
            SQLConnection.SQLUpdateCommand(sqlcmd2);
            SQLConnection.SQLUpdateCommand(sqlcmd3);
            SQLConnection.SQLUpdateCommand(sqlcmd4);

            //repaint
            refreshTypesTable();
        }
    }

    @Action
    public void deleteControllerType() {
        //Get the row we want to delete
        int row = table_controllertypes.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_controllertypes.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);

            //Run SQL Command
            String sqlcmd = "DELETE FROM controllertypes WHERE rowid=" + rowid + ";";
            SQLConnection.SQLUpdateCommand(sqlcmd);

            //Refresh the table
            refreshTypesTable();
        }
    }

    @Action
    public void showAddControllerBox() {
        if (newControllerBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newControllerBox = new NewControllerWindow(mainFrame, SQLConnection.SqlConnection);
            newControllerBox.setLocationRelativeTo(mainFrame);
            newControllerBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newControllerBox.setSize(435, 357);
            newControllerBox.setResizable(false);
            newControllerBox.setTitle("Add a New Modbus Slave Type");
            newControllerBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newControllerBox);
        refreshSetupTab();
    }

    ////////////////////////////////////////////////////////////////////////////
    //Setup Tab - Modbus Pages
    ////////////////////////////////////////////////////////////////////////////
    public void refreshPagesTable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, controllertype, page, registerstart, registerend, description FROM registerblocks "
                + "ORDER BY controllertype, page;";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Unique ID", "Controller Type", "Page", "Register Start", "Register End", "Long Description"};
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        if (rows > 0) {
            for (int count = rows; count == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_pages.setModel(aModel);
    }

    @Action
    public void showAddNewPageBox() {
        if (newPageBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newPageBox = new NewPageWindow(mainFrame, SQLConnection);
            newPageBox.setLocationRelativeTo(mainFrame);
            newPageBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newPageBox.setSize(435, 357);
            newPageBox.setResizable(false);
            newPageBox.setTitle("Add a New Page of Modbus Data");
            newPageBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newPageBox);
        refreshSetupTab();
    }

    @Action
    public void delete_page() {
        //Get the row we want to delete
        int row = table_pages.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_pages.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);
            String controller = (String) model.getValueAt(row, 1);
            Integer page = (Integer) model.getValueAt(row, 2);

            //Open SQL
            String sqlcmd = "DELETE FROM registerblocks WHERE rowid=" + rowid + ";";
            String sqlcmd2 = "DELETE FROM registerdetail WHERE page=" + page + " AND controllertype='" + controller + "'";
            SQLConnection.SQLUpdateCommand(sqlcmd);
            SQLConnection.SQLUpdateCommand(sqlcmd2);

            //Refresh the table
            refreshPagesTable();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //Setup Tab - Slaves
    ////////////////////////////////////////////////////////////////////////////
    @Action
    public void showAddNewSlaveBox() {
        if (newSlaveBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newSlaveBox = new NewSlaveWindow(mainFrame, SQLConnection);
            newSlaveBox.setLocationRelativeTo(mainFrame);
            newSlaveBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newSlaveBox.setResizable(false);
            newSlaveBox.setSize(316, 226);
            newSlaveBox.setTitle("Add a New Slave");
            newSlaveBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newSlaveBox);
        refreshSetupTab();
    }

    @Action
    public void applychangesToSlaves() {
        DefaultTableModel aModel = (DefaultTableModel) table_slaves.getModel();
        int rowCount = aModel.getRowCount();
        text_messages.setText(text_messages.getText().toString() + "\n" + "Updating Slaves");

        for (int i = 0; i < rowCount; i++) {
            Integer rowid = (Integer) aModel.getValueAt(i, 0);
            Integer modbusslaveid = (Integer) aModel.getValueAt(i, 1);
            String longname = (String) aModel.getValueAt(i, 2);
            String controllertype = (String) aModel.getValueAt(i, 3);

            String sqlcmd = "UPDATE slaves SET modbusslaveid=" + modbusslaveid
                    + ", " + "longname='" + longname + "', "
                    + "controllertype='" + controllertype + "' "
                    + "WHERE rowid=" + rowid + ";";

            SQLConnection.SQLUpdateCommand(sqlcmd);
        }
    }

    public void refreshSlaveTable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, modbusslaveid, longname, controllertype FROM slaves";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Unique ID", "SlaveID", "Long Name", "Controller Type"};
        DefaultTableModel aModel = new DefaultTableModel();
        //DefaultTableModel aModel = (DefaultTableModel) tableslaves.getModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();
        if (rows > 0) {
            for (int count = rows; count == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_slaves.setModel(aModel);

        //Create the drop down list
        TableColumn controllertypeColumn = table_slaves.getColumnModel().getColumn(3);
        JComboBox comboBox = new JComboBox();
        sqlcmd = "SELECT controllertype FROM controllertypes";
        resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            String value = (String) resultValues.get(0);
            comboBox.addItem(value);
        }

        controllertypeColumn.setCellEditor(new DefaultCellEditor(comboBox));

        //Set the integer objects to spinners
        TableColumn modbusSlaveColumn = table_slaves.getColumnModel().getColumn(1);
        modbusSlaveColumn.setCellEditor(new SpinnerEditor(1, 16, 1, 1));
        modbusSlaveColumn.setWidth(50);
    }

    @Action
    public void delete_slave() {
        //Get the row we want to delete
        int row = table_slaves.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_slaves.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);
            Integer slaveID = (Integer) model.getValueAt(row, 1);

            //Open SQL
            String sqlcmd = "DELETE FROM slaves WHERE rowid=" + rowid + ";";
            String sqlcmd2 = "DROP TABLE slave" + slaveID + ";";
            SQLConnection.SQLUpdateCommand(sqlcmd);
            SQLConnection.SQLUpdateCommand(sqlcmd2);

            //Refresh the table
            refreshSlaveTable();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //Setup Tab - RS485 Page
    ////////////////////////////////////////////////////////////////////////////
    public void refreshRS485Page() {
        updatePortsCombo();
        String sqlcmd = "SELECT serialport, baud, parity, databits, stopbits FROM rs485settings WHERE rowid=1;";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);
        List resultValues = (List) resultList.get(0); //row 1 only

        String serialport = (String) resultValues.get(0);
        Integer intbaud = (Integer) resultValues.get(1);
        String baud = String.valueOf(intbaud);
        String parity = (String) resultValues.get(2);
        Integer databits = (Integer) resultValues.get(3);
        Integer stopbits = (Integer) resultValues.get(4);

        //Now set the info on the page
        combo_serialports.setSelectedItem(serialport);
        combo_baud.setSelectedItem(baud);
        combo_parity.setSelectedItem(parity);
        spinner_databits.setValue(databits);
        spinner_stopbits.setValue(stopbits);
    }

    @Action
    public void updatePortsCombo() {
        //Fill the combo box with the available com ports
        List portlist = getComPorts();
        String[] portstring = null;
        combo_serialports.removeAllItems();

        for (int i = 0; i < portlist.size(); i++) {
            combo_serialports.addItem(portlist.get(i));
        }
    }

    public List getComPorts() {
        //Modifies path to include for Fedora yum install of rxtx
        //String path = System.getProperty("java.library.path");
        //System.setProperty("java.library.path", path + ":/usr/lib64/rxtx");

        //Get the ports
        Enumeration ports = CommPortIdentifier.getPortIdentifiers();

        //Setup and Enumerate the ports
        List portlist = new LinkedList();

        while (ports.hasMoreElements()) {
            CommPortIdentifier port = (CommPortIdentifier) ports.nextElement();

            String type;
            switch (port.getPortType()) {
                case CommPortIdentifier.PORT_SERIAL:
                    type = "Serial";
                    break;
                default: /// Shouldn't happen
                    type = "Unknown";
                    break;
            }
            portlist.add(port.getName());
        }
        return portlist;
    }

    @Action
    public void saveRS485() {
        String serialport = combo_serialports.getSelectedItem().toString();
        String baud = combo_baud.getSelectedItem().toString();
        String parity = combo_parity.getSelectedItem().toString();
        Integer databits = (Integer) spinner_databits.getValue();
        Integer stopbits = (Integer) spinner_stopbits.getValue();

        //Build the sql insert
        String sqlcmd = "UPDATE rs485settings SET serialport='"
                + serialport + "', baud=" + baud + ", "
                + "parity='" + parity + "', databits="
                + databits + ", stopbits=" + stopbits;
        SQLConnection.SQLUpdateCommand(sqlcmd);
    }
    ////////////////////////////////////////////////////////////////////////////
    //Modbus Loop
    ////////////////////////////////////////////////////////////////////////////
    private ModbusRS485Connection ModRS485Con;
    private ModbusReadMultipleRegistersConnection readMultRegisters;
    private ModbusWriteMultipleRegistersConnection writeMultRegisters;
    private boolean ModbusLoopComplete = true;

    public void ModbusRS485Start() {
        //init variable here to allow debug options to take place
        if (ModRS485Con == null) {
            ModRS485Con = new ModbusRS485Connection();
        }

        try {
            String serialport = combo_serialports.getSelectedItem().toString();
            String baud = combo_baud.getSelectedItem().toString();
            String parity = combo_parity.getSelectedItem().toString();
            Integer databits = (Integer) spinner_databits.getValue();
            Integer stopbits = (Integer) spinner_stopbits.getValue();

            //Setup the serial interface and open it
            ModRS485Con.CreateRS485Connection(serialport, baud, parity, databits, stopbits);
            ModRS485Con.OpenRS485Connection();
        } catch (Exception e) {
            addMessage("Unable to Open RS485 Connection, check port settings");
        }
    }

    public List getSlaves() {
        List slaveList = new LinkedList();
        String sqlcmd = "SELECT modbusslaveid FROM slaves;";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Integer value = (Integer) resultValues.get(0);
            slaveList.add(value);
        }

        return slaveList;
    }

    public List readMultipleRegisters(Integer RegStart, Integer RegEnd, Integer SlaveID) {
        Integer RegCount = RegEnd - RegStart + 1;
        readMultRegisters.ReadRequest(RegStart, RegCount, SlaveID);
        readMultRegisters.ExecuteTransaction(ModRS485Con.getSerialRS485Connection());
        List response = readMultRegisters.SlaveResponse();
        if (response.isEmpty()) {
            addMessage("Slave : " + SlaveID + " - No Data Received");
        }
        return response;
    }

    public void getModbusData(Integer SlaveID) {
        //Start by getting the controller type
        String sqlcmd = "SELECT controllertype FROM slaves WHERE modbusslaveid="
                + SlaveID + ";";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);
        List resultValues = (List) resultList.get(0); //row 1 only
        String controllertype = (String) resultValues.get(0);

        //Get the pages of data applicable to this controller
        //We have not embedded the modbus transaction with the SQL
        //so we write out a list and read that in later.
        sqlcmd = "SELECT registerstart, registerend, modbusFunctionType FROM registerblocks WHERE "
                + " controllertype='" + controllertype + "';";
        List ListMapRegisters = new LinkedList();
        int ModbusFunctionType = 3; //Default read multiple registers
        resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int z = 0; z < resultList.size(); z++) {
            resultValues = (List) resultList.get(z);
            Integer registers[] = {0, 0};
            registers[0] = (Integer) resultValues.get(0);
            registers[1] = (Integer) resultValues.get(1);
            ModbusFunctionType = (Integer) resultValues.get(2);
            ListMapRegisters.add(registers);
        }

        //Itterate the pages
        Integer pages = ListMapRegisters.size();
        if (pages > 0) {
            for (int i = 0; i < pages; i++) {
                Integer[] registers = (Integer[]) ListMapRegisters.get(i);
                switch (ModbusFunctionType) {
                    case 3: //Multiple Read Registers //Multiple Write Registers
                        List response = readMultipleRegisters(registers[0], registers[1], SlaveID);
                        if (!response.isEmpty()) {
                            writeDataToSQL(response, SlaveID, registers[0]);
                            writeModbusData(SlaveID);
                        }

                    //todo - other modbus func types
                }
            }
        }
    }

    public void writeDataToSQL(List response, int SlaveID, int StartRegisterValue) {
        //Write in the Data
        int size = response.size();
        SQLConnection.ClearBatch();

        for (int i = 0; i < size; i++) {
            int CurrentRegister = StartRegisterValue + i;

            //This is UNSIGNED Integer
            Long Value16Bit = Long.valueOf((Integer) response.get(i));

            //Put it into the database as a binary string.. integer useless at this point
            String Value16Binary = Long.toBinaryString(Value16Bit);

            //Pad to 16 bits in length
            while (Value16Binary.length() < 16) {
                String TempValue = "0" + Value16Binary;
                Value16Binary = TempValue;
            }

            String sqlcmd = "UPDATE slave" + SlaveID + " SET 16binary='"
                    + Value16Binary + "' , timestamp=NOW() WHERE register=" + CurrentRegister + ";";
            SQLConnection.AddToBatch(sqlcmd);
        }
        SQLConnection.SQLExecuteBatch();


        //Check if it is SIGNED
        SQLConnection.ClearBatch();
        String sqlcmd = "SELECT isSigned, is32bit, 16binary, rowid, lowbyteregister FROM slave" + SlaveID + ";";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        try {
            for (int i = 0; i < resultList.size(); i++) {
                //The register we are dealing with
                List resultValues = (List) resultList.get(i);

                //it would seem mysql tinyint can only be cast as boolean
                Boolean isSigned = (Boolean) resultValues.get(0);
                Boolean is32Bit = (Boolean) resultValues.get(1);
                String Value16Binary = (String) resultValues.get(2);
                Integer rowid = (Integer) resultValues.get(3);
                Integer lowbyteregister = (Integer) resultValues.get(4);

                //Deal with 16bit unsigned
                long integerResult = 0;

                if (!is32Bit && !isSigned) {
                    integerResult = Long.parseLong(Value16Binary, 2);
                }

                if (!is32Bit && isSigned) {
                    bit16IntHandler b16H = new bit16IntHandler(Value16Binary, true);
                    integerResult = b16H.getSignedIntValue();
                    //integerResult = convertUnsignedtoSigned(Value16Binary);
                }

                //Fill the 16bit data
                if (!is32Bit) {
                    String hex16 = "0x" + Long.toHexString(integerResult);
                    sqlcmd = "UPDATE slave" + SlaveID + " SET 16integer=" + integerResult + ", "
                            + "16hex='" + hex16 + "' WHERE rowid=" + rowid + ";";
                    SQLConnection.AddToBatch(sqlcmd);
                } else {
                    //Only deal with the HSB side of the 32 register
                    if (lowbyteregister > 0) {
                        //The next register along so we have the LSB for 32 bit
                        List resultValuesPlusOne = (List) resultList.get(i + 1);
                        String Value16BinaryLB = (String) resultValuesPlusOne.get(2);

                        //Concat the two binary strings
                        String BinaryData32bits = Value16Binary + Value16BinaryLB;
                        Long result;

                        //This is UNSIGNED.. so we need to decide to sign or not
                        if (!isSigned) {
                            bit32IntHandler b32H = new bit32IntHandler(BinaryData32bits, true);
                            result = b32H.getUnSignedIntValue();
                        } else {
                            bit32IntHandler b32H = new bit32IntHandler(BinaryData32bits, false);
                            result = b32H.getSignedIntValue();
                        }

                        //Fill the 32bit data
                        String hex32 = "0x" + Long.toHexString(result);
                        sqlcmd = "UPDATE slave" + SlaveID + " SET 32integer=" + result + ", "
                                + "32hex='" + hex32 + "', 32binary='" + BinaryData32bits
                                + "' WHERE rowid=" + rowid + ";";
                        SQLConnection.AddToBatch(sqlcmd);
                    }
                }
            }
            SQLConnection.SQLExecuteBatch();
        } catch (Exception e) {
            addMessage("Error with SQL Command Listing Batch Out : ");
            for (int i = 0; i < SQLConnection.BatchSQLCommands.size(); i++) {
                addMessage(SQLConnection.BatchSQLCommands.get(i).toString());
            }
            e.printStackTrace();
            return;
        }
    }

    public void writeModbusData(int SlaveID) {
        String sqlcmd = "SELECT register, writedata, is32bit, isSigned"
                + " FROM slave" + SlaveID + " WHERE changeflag=1;";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        //Reset the write data and changeflag
        sqlcmd = "UPDATE slave" + SlaveID + " SET changeflag=0";
        SQLConnection.SQLUpdateCommand(sqlcmd);

        //Setup
        Long writedata = 0L;
        Integer startRegister = 0;

        //Cycle through writing modbus data
        int i = 0;
        while (i < resultList.size()) {
            //clear the Modbus data
            writeMultRegisters.clearData();

            List resultValues = (List) resultList.get(i);
            boolean is32bit = (Boolean) resultValues.get(2);
            Integer data = (Integer) resultValues.get(1);
            Integer register = (Integer) resultValues.get(0);
            boolean isSigned = (Boolean) resultValues.get(3);

            if (!is32bit) {
                //All the values stored are Signed, so convert to what we want

                int16to16binary i16 = new int16to16binary(data, isSigned);

                //Add the data to the request
                writeMultRegisters.addData(i16.getIntValue());
                writeMultRegisters.WriteRequest(register, SlaveID);
                writeMultRegisters.ExecuteTransaction(ModRS485Con.getSerialRS485Connection());
            } else {
                int32to16binary i32 = new int32to16binary(data, isSigned);

                //Add the data to the request
                writeMultRegisters.addData(i32.getHSBIntValue());
                writeMultRegisters.addData(i32.getLSBIntValue());
                writeMultRegisters.WriteRequest(register, SlaveID);
                writeMultRegisters.ExecuteTransaction(ModRS485Con.getSerialRS485Connection());
            }
            i++;
        }
    }

    //TEST AREA
    @Action
    public void test() {
        //readMultRegisters.ReadRequest(1792, 2, 4);
        // readMultRegisters.ExecuteTransaction(ModRS485Con.getSerialRS485Connection());
        // List response = readMultRegisters.SlaveResponse();


        System.err.println("Test Data Transmit");
        writeMultRegisters.clearData();
        writeMultRegisters.addData(0);
        writeMultRegisters.addData(4321);
        writeMultRegisters.WriteRequest(1810, 4);
        writeMultRegisters.ExecuteTransaction(ModRS485Con.getSerialRS485Connection());
    }

    public void closeModbusConnection() {
        ModRS485Con.DestroyRS485Connection();
    }

    //Called by timer event StartModbusTicker
    public void ModbusLoop() {
        //init the modbus variables
        if (writeMultRegisters == null) {
            writeMultRegisters = new ModbusWriteMultipleRegistersConnection();
        }
        if (readMultRegisters == null) {
            readMultRegisters = new ModbusReadMultipleRegistersConnection();
        }

        boolean rs485Open = false;
        try {
            if (ModRS485Con.isRS485Open()) {
                rs485Open = true;
            } else {
                ModbusRS485Start();
            }
        } catch (Exception e) {
            addMessage("No RS485 Connection Attempting to Start");
            ModbusRS485Start();
            return;
        }

        if (rs485Open) {
            List slaveList = getSlaves();
            int i = 0;

            while (i < slaveList.size()) {
                if (ModbusLoopComplete) {
                    ModbusLoopComplete = false;
                    Integer SlaveID = (Integer) slaveList.get(i);
                    getModbusData(SlaveID);
                    CheckAlarmPresent();
                    AnnunciatorStatus();
                    ModbusLoopComplete = true;
                    i++;
                }
            }
        }
    }

    @Action
    public void getAllSlaveData() {
        StartModbusTicker();
    }

    ////////////////////////////////////////////////////////////////////////////
    //WatchDog Timer - Checks SQL data is current
    ////////////////////////////////////////////////////////////////////////////
    public void watchdog() {
        boolean isDataOk = true;
        //Get the slaves
        List slaveList = getSlaves();

        //Utilise the SQL server clock to check its own data
        int time = modbus2sql.MYSQL_MODBUS_DATA_CURRENT;

        for (int i = 0; i < slaveList.size(); i++) {
            Integer SlaveID = (Integer) slaveList.get(i);
            String sqlcmd = "SELECT if((NOW()-timestamp)>" + time
                    + ",0,1) FROM slave" + SlaveID;
            List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

            for (int z = 0; z < resultList.size(); z++) {
                List resultValues = (List) resultList.get(z);

                for (int x = 0; x < resultValues.size(); x++) {
                    Long result = (Long) resultValues.get(x);

                    if (result == 0) {
                        isDataOk = false;
                        break;
                    }
                }
            }

            if (isDataOk) {
                sqlcmd = "UPDATE slave" + SlaveID + " SET livedata=1;";
            } else {
                sqlcmd = "UPDATE slave" + SlaveID + " SET livedata=0;";
            }

            SQLConnection.SQLUpdateCommand(sqlcmd);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //Viewer Tab
    ////////////////////////////////////////////////////////////////////////////
    @Action
    public void initViewerTab() {
        fillViewerComboBox();
    }

    public void fillViewerComboBox() {
        List slaveNames = new LinkedList();
        String sqlcmd = "SELECT modbusslaveid, longname FROM slaves;";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);
        combo_viewerslave.removeAllItems();

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Integer slaveID = (Integer) resultValues.get(0);
            String combo_item = "SlaveID:" + resultValues.get(0) + " - " + resultValues.get(1);
            combo_viewerslave.addItem(combo_item);
        }
    }

    @Action
    public void refreshViewerTable() {
        String comboViewer = combo_viewerslave.getSelectedItem().toString();
        int startPoint = comboViewer.indexOf(":") + 1;
        int endPoint = comboViewer.indexOf("-") - 1;
        String slaveIDString = comboViewer.substring(startPoint, endPoint);
        Integer SlaveID = Integer.valueOf(slaveIDString);
        fillViewerTable(SlaveID);
    }

    public void fillViewerTable(Integer SlaveID) {
        //Now fill the table... dyamically build the columns
        String sqlcmd = "SHOW columns FROM slave" + SlaveID + ";";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);
        List resultValues = (List) resultList.get(0);
        String[] tableHeader = new String[resultList.size()];

        //Itterate the columns to build a select command
        sqlcmd = "SELECT ";

        for (int i = 0; i < resultList.size(); i++) {
            resultValues = (List) resultList.get(i);
            String column = resultValues.get(0).toString();
            tableHeader[i] = column;
            String concat = sqlcmd + column + ", ";
            sqlcmd = concat;
        }

        //Strip trailing ,
        String sqlcmd2 = sqlcmd.substring(0, sqlcmd.length() - 2);
        sqlcmd = sqlcmd2 + " FROM slave" + SlaveID + ";";

        // TableModel definition
        //String[] tableColumnsName = (String[]) resultValues.toArray(new String[0]);
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableHeader);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        if (rows > 0) {
            for (int count = rows; count == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        // Loop through the ResultSet and transfer in the Model
        resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int z = 0; z < resultList.size(); z++) {
            resultValues = (List) resultList.get(z);
            Object[] objects = new Object[resultValues.size()];

            for (int i = 0; i < resultValues.size(); i++) {
                objects[i] = resultValues.get(i);
            }
            aModel.addRow(objects);
        }

        //Draw the table
        table_viewer.setModel(aModel);
        correctHeader(table_viewer);

        //if data is old paint it red
        sqlcmd = "SELECT livedata, register, 16binary, 16integer, 32integer, description FROM slave" + SlaveID + ";";
        resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        int rowCount = 0;
        List rowsList = new LinkedList();
        List toolTipList = new LinkedList();

        for (int z = 0; z < resultList.size(); z++) {
            resultValues = (List) resultList.get(z);
            String textToolTip = "Register:" + resultValues.get(1)
                    + " Desc:" + resultValues.get(5)
                    + " Integer:" + resultValues.get(3) + "\n"
                    + " Binary: " + resultValues.get(2)
                    + " 32bit Computed:" + resultValues.get(4);

            toolTipList.add(textToolTip);

            if ((Boolean) resultValues.get(0)) {
                rowsList.add(rowCount);
            }
            rowCount++;
        }

        //Create a custom renderer using com.enrogen.CustomRenderer
        tableRenderer TR = new tableRenderer();
        TR.setTableHighlightON();
        TR.setToolTipDelays(0, 5000);
        TR.setHighLightRowsList(rowsList);
        TR.setToolTipList(toolTipList);

        //Apply it to the columns
        for (int i = 0; i < table_viewer.getColumnCount(); i++) {
            TableColumn col = table_viewer.getColumnModel().getColumn(i);
            col.setCellRenderer(TR);
            col.setPreferredWidth(200);
        }
    }

    //http://bugs.sun.com/view_bug.do?bug_id=4473075
    private void correctHeader(JTable table) {
        Dimension headerSize = new Dimension(table.getWidth(), 50);
        table.getTableHeader().setPreferredSize(headerSize);
    }

    @Action
    public void viewer_auto_refresh_check() {
        boolean isSelected = check_viewer_auto_refresh.isSelected();

        if (isSelected) {
            StartAutoRefreshTicker();
        } else {
            autoRefreshTimer.stop();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //Alarms Monitoring
    ////////////////////////////////////////////////////////////////////////////
    public void refreshAlarmsFlagTable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, controllertype, warningregister, warningbit, "
                + "shutdownregister, shutdownbit, tripregister, tripbit "
                + "FROM alarmflags";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Unique ID", "Controller", "Warn Reg", "Warn Bit", "S/D Reg",
            "S/D Bit", "Trip Reg", "Trip Bit"};
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        if (rows > 0) {
            for (int count = rows; count
                    == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_alarmflags.setModel(aModel);
    }

    public void refreshAlarmsMonitoredTable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, controllertype, alarmstring, modbusregister, startbit, bitqty, valuedisabled, valuewarning,"
                + "valueshutdown, valueunimplemented FROM alarmtypes";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Unique ID", "Control", "Alarm Desc", "Modbus Reg", "Bit Start", "QtyBits", "Val Disabled",
            "Val Warn", "Val Shutdown", "Val Unimplmented"};
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        if (rows > 0) {
            for (int count = rows; count
                    == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z
                < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_monitoredalarms.setModel(aModel);
    }

    public void refreshAlarmAnnunciatortable() {
        //Now fill the table
        String sqlcmd = "SELECT rowid, slaveID, warnregister, warnregisterbit, tripregister, "
                + "tripregisterbit, shutdownregister, shutdownregisterbit, sendresetflag, "
                + "sendresetreg, sendresetbit, recvresetflag, recvresetreg, recvresetbit "
                + "FROM alarmannunciator";
        List resultList = SQLConnection.SQLSelectCommand(sqlcmd);

        // TableModel definition
        String[] tableColumnsName = {"Uni ID", "Slave Id", "Warn Reg", "Warn Bit",
            "Trip Reg", "Trip Bit", "S/D Reg", "S/D Bit",
            "Send Flag", "Send Reg", "Send Bit", "Recv Flag", "Recv Reg", "Recv Bit"};
        DefaultTableModel aModel = new DefaultTableModel();
        aModel.setColumnIdentifiers(tableColumnsName);

        //If Tablemodel has existing rows delete them
        int rows = aModel.getRowCount();

        if (rows > 0) {
            for (int count = rows; count
                    == 0; count--) {
                aModel.removeRow(count - 1);
            }
        }

        for (int z = 0; z < resultList.size(); z++) {
            List resultValues = (List) resultList.get(z);
            Object[] objects = resultValues.toArray();
            aModel.addRow(objects);
        }
        table_alarmAnnunciator.setModel(aModel);
    }

    @Action
    public void showAddNewAlarmFlagBox() {
        if (newAlarmFlagBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newAlarmFlagBox = new NewAlarmFlagWindow(mainFrame, SQLConnection);
            newAlarmFlagBox.setLocationRelativeTo(mainFrame);
            newAlarmFlagBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newAlarmFlagBox.setSize(435, 357);
            newAlarmFlagBox.setResizable(false);
            newAlarmFlagBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newAlarmFlagBox);
        refreshSetupTab();
    }

    @Action
    public void DeleteAlarmFlagBox() {
        //Get the row we want to delete
        int row = table_alarmflags.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_alarmflags.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);
            String controllertype = (String) model.getValueAt(row, 1);

            //Open SQL
            String sqlcmd = "DELETE FROM alarmflags WHERE rowid=" + rowid + ";";
            String sqlcmd2 = "DELETE FROM alarmtypes WHERE controllertype='" + controllertype + "';";
            SQLConnection.SQLUpdateCommand(sqlcmd);
            SQLConnection.SQLUpdateCommand(sqlcmd2);

            //Refresh the table
            refreshSlaveTable();
        }
        refreshSetupTab();
    }

    @Action
    public void showAddNewAlarmBox() {
        if (newAlarmBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newAlarmBox = new NewAlarmWindow(mainFrame, SQLConnection);
            newAlarmBox.setLocationRelativeTo(mainFrame);
            newAlarmBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newAlarmBox.setSize(435, 357);
            newAlarmBox.setResizable(false);
            newAlarmBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newAlarmBox);
        refreshSetupTab();
    }

    @Action
    public void DeleteAlarm() {
        //Get the row we want to delete
        int row = table_monitoredalarms.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_monitoredalarms.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);
            String controllertype = (String) model.getValueAt(row, 1);

            //Open SQL
            String sqlcmd = "DELETE FROM alarmtypes WHERE rowid=" + rowid + ";";
            SQLConnection.SQLUpdateCommand(sqlcmd);

            //Refresh the table
            refreshSlaveTable();
        }
        refreshSetupTab();
    }

    @Action
    public void DeleteAllAlarm() {
        //Get the row we want to delete
        int row = table_monitoredalarms.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_monitoredalarms.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);
            String controllertype = (String) model.getValueAt(row, 1);

            //Open SQL
            String sqlcmd = "DELETE FROM alarmtypes WHERE controllertype='" + controllertype + "';";
            SQLConnection.SQLUpdateCommand(sqlcmd);

            //Refresh the table
            refreshSlaveTable();
        }
        refreshSetupTab();
    }

    @Action
    public void showAddNewAlarmAnnunciatorBox() {
        if (newAlarmAnnunciatorBox == null) {
            JFrame mainFrame = Modbus2SQLApp.getApplication().getMainFrame();
            newAlarmAnnunciatorBox = new NewAlarmAnnunciatorWindow(mainFrame, SQLConnection);
            newAlarmAnnunciatorBox.setLocationRelativeTo(mainFrame);
            newAlarmAnnunciatorBox.setModalityType(ModalityType.APPLICATION_MODAL);
            newAlarmAnnunciatorBox.setSize(435, 357);
            newAlarmAnnunciatorBox.setResizable(false);
            newAlarmAnnunciatorBox.pack();
        }
        Modbus2SQLApp.getApplication().show(newAlarmAnnunciatorBox);
        refreshSetupTab();
    }

    @Action
    public void DeleteAnnunciator() {
        //Get the row we want to delete
        int row = table_alarmAnnunciator.getSelectedRow();
        DefaultTableModel model = (DefaultTableModel) table_alarmAnnunciator.getModel();

        if (row >= 0) {
            Integer rowid = (Integer) model.getValueAt(row, 0);

            //Open SQL
            String sqlcmd = "DELETE FROM alarmannunciator WHERE rowid=" + rowid + ";";
            SQLConnection.SQLUpdateCommand(sqlcmd);

            //Refresh the table
            refreshSlaveTable();
        }
        refreshSetupTab();
    }

    ////////////////////////////////////////////////////////////////////////////
    //Alarms Logging Loop
    ////////////////////////////////////////////////////////////////////////////
    public void CheckAlarmPresent() {
        String sqlcmd = "SELECT controllertype, warningregister, warningbit, "
                + "shutdownregister, shutdownbit, tripregister, tripbit FROM "
                + "alarmflags";
        List alarmList = SQLConnection.SQLSelectCommand(sqlcmd);

        sqlcmd = "SELECT modbusslaveid, controllertype FROM slaves;";
        List controllerList = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int x = 0; x < controllerList.size(); x++) {
            List controllerRowList = (List) controllerList.get(x);
            Integer modbusslaveID = (Integer) controllerRowList.get(0);
            String controllertype = (String) controllerRowList.get(1);

            for (int z = 0; z < alarmList.size(); z++) {
                List resultValues = (List) alarmList.get(z);

                if (controllertype.compareTo((String) resultValues.get(0)) == 0) {

                    Integer warningregister = (Integer) resultValues.get(1);
                    Integer warningbit = (Integer) resultValues.get(2);
                    Integer shutdownregister = (Integer) resultValues.get(3);
                    Integer shutdownbit = (Integer) resultValues.get(4);
                    Integer tripregister = (Integer) resultValues.get(5);
                    Integer tripbit = (Integer) resultValues.get(6);

                    String sqlWarningActive = "SELECT 16binary FROM slave"
                            + modbusslaveID + " WHERE register=" + warningregister;
                    String sqlShutdownActive;
                    String sqlTripActive;

                    String binaryWarning;
                    String binaryShutdown;
                    String binaryTrip;

                    //Get the first result
                    List resultWarning = SQLConnection.SQLSelectCommand(sqlWarningActive);

                    try {
                        binaryWarning = (String) ((List) resultWarning.get(0)).get(0);

                        //Cut out unnecessary SQL work
                        if (warningregister == shutdownregister) {
                            binaryShutdown = binaryWarning;
                        } else {
                            sqlShutdownActive = "SELECT 16binary FROM slave"
                                    + modbusslaveID + " WHERE register=" + shutdownregister;
                            List resultShutdown = SQLConnection.SQLSelectCommand(sqlShutdownActive);
                            binaryShutdown = (String) ((List) resultShutdown.get(0)).get(0);
                        }

                        if (warningregister == tripregister) {
                            binaryTrip = binaryWarning;
                        } else {
                            sqlTripActive = "SELECT 16binary FROM slave"
                                    + modbusslaveID + " WHERE register=" + tripregister;
                            List resultTrip = SQLConnection.SQLSelectCommand(sqlTripActive);
                            binaryTrip = (String) ((List) resultTrip.get(0)).get(0);
                        }

                        int warningPos = -1 * (warningbit - 16);
                        int shutdownPos = -1 * (shutdownbit - 16);
                        int tripPos = -1 * (tripbit - 16);

                        if (binaryWarning.substring(warningPos, warningPos + 1).compareTo("1") == 0) {
                            identifyAlarm(controllertype, modbusslaveID, 0);
                        }

                        if (binaryShutdown.substring(shutdownPos, shutdownPos + 1).compareTo("1") == 0) {
                            identifyAlarm(controllertype, modbusslaveID, 0);
                        }

                        if (binaryTrip.substring(tripPos, tripPos + 1).compareTo("1") == 0) {
                            identifyAlarm(controllertype, modbusslaveID, 0);
                        }
                    } catch (Exception e) {
                        System.err.println("Could not identify alarms... have you added the register page containing alarm register?");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void identifyAlarm(String ControllerType, Integer SlaveID, int alarmtype) {
        String sqlcmd = "SELECT alarmstring, modbusregister, startbit, bitqty, "
                + "modbusregisterdescription, registerqtydescription, valuedisabled, "
                + "valuehealthy, valuewarning, valueshutdown, valuetrip, valueunimplemented "
                + "FROM alarmtypes WHERE controllertype='" + ControllerType + "';";

        List AlarmInfo = SQLConnection.SQLSelectCommand(sqlcmd);
        sqlcmd = "SELECT register, 16binary FROM slave" + SlaveID + ";";

        List registers = SQLConnection.SQLSelectCommand(sqlcmd);

        //Build a hashmap we can use a lookup on
        HashMap registersMap = new HashMap();

        for (int i = 0; i < registers.size(); i++) {
            List registersRow = (List) registers.get(i);
            registersMap.put((Integer) registersRow.get(0), (String) registersRow.get(1));
        }

        //Cycle through the alarms
        for (int i = 0; i < AlarmInfo.size(); i++) {
            //Get the modbus register to be dealt with
            List AlarmInfoRow = (List) AlarmInfo.get(i);

            //Read each row out
            String alarmstring = (String) AlarmInfoRow.get(0);
            Integer modbusregister = (Integer) AlarmInfoRow.get(1);
            Integer startbit = (Integer) AlarmInfoRow.get(2);
            Integer bitqty = (Integer) AlarmInfoRow.get(3);
            Integer modbusregisterdescription = (Integer) AlarmInfoRow.get(4);
            Integer registerqtydescription = (Integer) AlarmInfoRow.get(5);
            Integer valuedisabled = (Integer) AlarmInfoRow.get(6);
            Integer valuehealthy = (Integer) AlarmInfoRow.get(7);
            Integer valuewarning = (Integer) AlarmInfoRow.get(8);
            Integer valueshutdown = (Integer) AlarmInfoRow.get(9);
            Integer valuetrip = (Integer) AlarmInfoRow.get(10);
            Integer valueunimplemented = (Integer) AlarmInfoRow.get(11);

            //Find the registers current value
            String binary = (String) registersMap.get(modbusregister);

            //Break the string into appropriate section
            int pos = -1 * (startbit - 16);

            try {
                String AlarmStatus = binary.substring(pos, pos + bitqty);
                int AlarmStatusValue = Integer.valueOf(AlarmStatus, 2);

                // System.out.println(modbusregister + ":" + binary + ":" + AlarmStatus + ":" + AlarmStatusValue + ":" + valuewarning);

                //System.out.println(modbusregister + ":" + binary + ":" + AlarmStatus + ":" + AlarmStatusValue + ":" + valueshutdown);
                //System.out.println(alarmstring);

                if (AlarmStatusValue == valueshutdown) {
                    logAlarm(alarmstring, SlaveID, "Shutdown");
                }

                if (AlarmStatusValue == valuewarning) {
                    logAlarm(alarmstring, SlaveID, "Warning");
                }

                if (AlarmStatusValue == valuetrip) {
                    logAlarm(alarmstring, SlaveID, "Trip");
                }
            } catch (Exception e) {
                System.out.println("Could not find alarm register in slave table : " + modbusregister);
                logAlarm("Un-Named Alarm", SlaveID, "Warning");
            }
        }
    }

    public void logAlarm(String alarm, int slave, String type) {
        //If alarm exists in log and is not acknowledged we do not need to log it
        String sqlcmd = "SELECT slaveid, alarmdesc FROM alarmlog WHERE alarmacknowledged=0";
        List result = SQLConnection.SQLSelectCommand(sqlcmd);

        //Check if its already logged and unacknowledged
        boolean alarmexists = false;

        for (int i = 0; i < result.size(); i++) {
            List thisrow = (List) result.get(i);
            int slaveid = (Integer) thisrow.get(0);
            String alarmdesc = (String) thisrow.get(1);

            if (slave == slaveid) {
                if (alarm.compareTo(alarmdesc) == 0) {
                    alarmexists = true;
                }
            }
        }

        if (!alarmexists) {
            //Get the slaves name
            sqlcmd = "SELECT longname FROM slaves WHERE modbusslaveid=" + slave + ";";
            result = SQLConnection.SQLSelectCommand(sqlcmd);
            List row1 = (List) result.get(0);
            String longname = (String) row1.get(0);

            //Now insert it
            sqlcmd = "INSERT INTO alarmlog SET curdate=curdate(), curtime=curtime(), "
                    + "slaveid=" + slave
                    + ", slavename='" + longname
                    + "', alarmdesc='" + alarm
                    + "', alarmtype='" + type
                    + "', alarmacknowledged=0 "
                    + ", alarmreset=0"
                    + ", alarmnotifications=1";
            SQLConnection.SQLUpdateCommand(sqlcmd);

            //Now Annunciate it
            AnnunciateAlarm(type);
        }
    }
    ////////////////////////////////////////////////////////////////////////////
    //Alarms Modbus Annunciator
    ////////////////////////////////////////////////////////////////////////////

    public void AnnunciatorStatus() {
        String sqlcmd = "SELECT slaveID, hmiresetflag, recvresetreg, recvresetbit FROM "
                + "alarmannunciator; ";
        List rows = SQLConnection.SQLSelectCommand(sqlcmd);

        boolean hmiresetflag = false;

        for (int i = 0; i < rows.size(); i++) {
            List thisrow = (List) rows.get(i);
            Integer slaveid = (Integer) thisrow.get(0);
            Integer hmireset = (Integer) thisrow.get(1);
            Integer recvresetreg = (Integer) thisrow.get(2);
            Integer recvresetbit = (Integer) thisrow.get(3);

            //Check if the HMI software was flagged a reset
            if (hmireset == 1) {
                System.out.println("Reset Flag from HMI is ON");
                hmiresetflag = true;
            }

            //Check the actual annunciator for reset.. eg reset button
            sqlcmd = "SELECT 16binary FROM slave" + slaveid
                    + " WHERE register=" + recvresetreg;
            List rows2 = SQLConnection.SQLSelectCommand(sqlcmd);
            for (int x = 0; x < rows.size(); x++) {
                List result = (List) rows2.get(x);
                String bin16 = (String) result.get(0);
                int bitPos = -1 * (recvresetbit - 16);
                if (bin16.substring(bitPos, bitPos + 1).compareTo("1") == 0) {
                    System.out.println("Reset Flag from Annunciator is ON");
                    hmiresetflag = true;
                }
            }
        }

        if (hmiresetflag) {
            ResetAnnunciator();
            AcknowledgeAllAlarms();
        }
    }

    public void AcknowledgeAllAlarms() {
        String sqlcmd = "UPDATE alarmlog SET alarmacknowledged=1;";
        SQLConnection.SQLUpdateCommand(sqlcmd);
    }

    public void ResetAnnunciator() {
        String sqlcmd = "SELECT slaveID, warnregister, warnregisterbit, tripregister, "
                + "tripregisterbit, shutdownregister, shutdownregisterbit, sendresetreg, sendresetbit FROM "
                + "alarmannunciator";
        List rows = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int i = 0; i < rows.size(); i++) {
            List thisrow = (List) rows.get(i);
            Integer slaveid = (Integer) thisrow.get(0);
            Integer warnregister = (Integer) thisrow.get(1);
            Integer warnbit = (Integer) thisrow.get(2);
            Integer tripregister = (Integer) thisrow.get(3);
            Integer tripbit = (Integer) thisrow.get(4);
            Integer shutdownregister = (Integer) thisrow.get(5);
            Integer shutdownregisterbit = (Integer) thisrow.get(6);
            Integer sendresetreg = (Integer) thisrow.get(7);
            Integer sendresetregbit = (Integer) thisrow.get(8);

            //Work out the int values
            //Bit to position in string
            //Bits 1 to 16!
            int warningPos = -1 * (warnbit - 16);
            int shutdownPos = -1 * (shutdownregisterbit - 16);
            int tripPos = -1 * (tripbit - 16);

            //Prepare SQL Batch
            SQLConnection.ClearBatch();

            //WARNING RESET
            //Get the current resgiter value as binary string
            sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                    + "register=" + warnregister + ";";
            List alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
            List result = (List) alarmrow.get(0);
            String bin16 = (String) result.get(0);

            //Replace the relevant bit
            bin16 = bin16.substring(0, warningPos) + "0" + bin16.substring(warningPos + 1);
            System.out.println(bin16);
            int int16 = Integer.parseInt(bin16, 2);

            //Workaround .. this has bugs
            int16 =0;

            //Write new value
            sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                    + ", changeflag=1 WHERE register=" + warnregister;

            SQLConnection.AddToBatch(sqlcmd);


            //SHUTDOWN RESET
            //Get the current resgiter value as binary string
            sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                    + "register=" + shutdownregister + ";";
            alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
            result = (List) alarmrow.get(0);
            bin16 = (String) result.get(0);

            //Replace the relevant bit
            bin16 = bin16.substring(0, shutdownPos) + "0" + bin16.substring(shutdownPos + 1);
            int16 = Integer.parseInt(bin16, 2);
            
            //Workaround .. this has bugs
            int16 =0;

            //Write new value
            sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                    + ", changeflag=1 WHERE register=" + shutdownregister;

            SQLConnection.AddToBatch(sqlcmd);


            //ANNUNCIATOR RESET
            //Get the current resgiter value as binary string
            sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                    + "register=" + sendresetreg + ";";
            alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
            result = (List) alarmrow.get(0);
            bin16 = (String) result.get(0);

            //Replace the relevant bit
            bin16 = bin16.substring(0, sendresetregbit) + "0" + bin16.substring(sendresetregbit + 1);
            int16 = Integer.parseInt(bin16, 2);

            //Workaround .. this has bugs
            int16 =0;

            //Write new value
            sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                    + ", changeflag=1 WHERE register=" + sendresetreg;

            SQLConnection.AddToBatch(sqlcmd);

            //TRIP RESET
            //Get the current resgiter value as binary string
            sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                    + "register=" + tripregister + ";";
            alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
            result = (List) alarmrow.get(0);
            bin16 = (String) result.get(0);

            //Replace the relevant bit
            bin16 = bin16.substring(0, tripPos) + "0" + bin16.substring(tripPos + 1);
            int16 = Integer.parseInt(bin16, 2);

            //Workaround .. this has bugs
            int16 =0;

            //Write new value
            sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                    + ", changeflag=1 WHERE register=" + tripregister;

            SQLConnection.AddToBatch(sqlcmd);

            //Execute the SQL Batch
            sqlcmd = "UPDATE alarmannunciator SET hmiresetflag=0;";
            SQLConnection.AddToBatch(sqlcmd);
            SQLConnection.SQLExecuteBatch();
        }
    }

    public void AnnunciateAlarm(String alarmtype) {
        String sqlcmd = "SELECT slaveID, warnregister, warnregisterbit, tripregister, "
                + "tripregisterbit, shutdownregister, shutdownregisterbit FROM "
                + "alarmannunciator";
        List rows = SQLConnection.SQLSelectCommand(sqlcmd);

        for (int i = 0; i < rows.size(); i++) {
            List thisrow = (List) rows.get(i);
            Integer slaveid = (Integer) thisrow.get(0);
            Integer warnregister = (Integer) thisrow.get(1);
            Integer warnbit = (Integer) thisrow.get(2);
            Integer tripregister = (Integer) thisrow.get(3);
            Integer tripbit = (Integer) thisrow.get(4);
            Integer shutdownregister = (Integer) thisrow.get(5);
            Integer shutdownregisterbit = (Integer) thisrow.get(6);

            //Work out the int values
            //Bit to position in string
            int warningPos = -1 * (warnbit - 16);
            int shutdownPos = -1 * (shutdownregisterbit - 16);
            int tripPos = -1 * (tripbit - 16);

            if (alarmtype.compareTo("Warning") == 0) {
                //Get the current resgiter value as binary string
                sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                        + "register=" + warnregister + ";";
                List alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
                List result = (List) alarmrow.get(0);
                String bin16 = (String) result.get(0);

                //Replace the relevant bit
                bin16 = bin16.substring(0, warningPos) + "1" + bin16.substring(warningPos + 1);
                int int16 = Integer.parseInt(bin16, 2);

                //Write new value
                sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                        + ", changeflag=1 WHERE register=" + warnregister;

                SQLConnection.SQLUpdateCommand(sqlcmd);
            }

            if (alarmtype.compareTo("Shutdown") == 0) {
                //Get the current resgiter value as binary string
                sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                        + "register=" + shutdownregister + ";";
                List alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
                List result = (List) alarmrow.get(0);
                String bin16 = (String) result.get(0);

                //Replace the relevant bit
                bin16 = bin16.substring(0, shutdownPos) + "1" + bin16.substring(shutdownPos + 1);
                int int16 = Integer.parseInt(bin16, 2);

                //Write new value
                sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                        + ", changeflag=1 WHERE register=" + shutdownregister;

                SQLConnection.SQLUpdateCommand(sqlcmd);
            }

            if (alarmtype.compareTo("Trip") == 0) {
                //Get the current resgiter value as binary string
                sqlcmd = "SELECT 16binary FROM slave" + slaveid + " WHERE "
                        + "register=" + tripregister + ";";
                List alarmrow = SQLConnection.SQLSelectCommand(sqlcmd);
                List result = (List) alarmrow.get(0);
                String bin16 = (String) result.get(0);

                //Replace the relevant bit
                bin16 = bin16.substring(0, tripPos) + "1" + bin16.substring(tripPos + 1);
                int int16 = Integer.parseInt(bin16, 2);

                //Write new value
                sqlcmd = "UPDATE slave" + slaveid + " SET writedata=" + int16
                        + ", changeflag=1 WHERE register=" + tripregister;

                SQLConnection.SQLUpdateCommand(sqlcmd);
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainPanel = new javax.swing.JPanel();
        mainTabbedPanel = new javax.swing.JTabbedPane();
        mysqlPanel = new javax.swing.JPanel();
        jPanel9 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        text_sqlserverip = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        text_sqlserverport = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        text_sqlusername = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        text_sqldatabasename = new javax.swing.JTextField();
        btn_sqlsave = new javax.swing.JButton();
        text_sqlpassword = new javax.swing.JTextField();
        jLabel17 = new javax.swing.JLabel();
        btn_sqlcancel = new javax.swing.JButton();
        btn_sqledit = new javax.swing.JButton();
        jPanel10 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        greenlamp = new javax.swing.JLabel();
        redlamp = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jPanel12 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        btnStart = new javax.swing.JButton();
        btnStop = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        checkStartModbusAuto = new javax.swing.JCheckBox();
        setupPanel = new javax.swing.JTabbedPane();
        jPanel8 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        jScrollPane5 = new javax.swing.JScrollPane();
        table_controllertypes = new javax.swing.JTable();
        jButton1 = new javax.swing.JButton();
        jButton7 = new javax.swing.JButton();
        jButton8 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        btn_addnewPage = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        table_pages = new javax.swing.JTable();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jPanel6 = new javax.swing.JPanel();
        btn_apply2 = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        table_slaves = new javax.swing.JTable();
        jButton3 = new javax.swing.JButton();
        btn_insertNewSlave = new javax.swing.JButton();
        jLabel16 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        combo_baud = new javax.swing.JComboBox();
        jLabel11 = new javax.swing.JLabel();
        combo_parity = new javax.swing.JComboBox();
        spinner_databits = new javax.swing.JSpinner();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        spinner_stopbits = new javax.swing.JSpinner();
        jLabel14 = new javax.swing.JLabel();
        combo_serialports = new javax.swing.JComboBox();
        btn_saveRS485 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel16 = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        table_alarmflags = new javax.swing.JTable();
        jButton10 = new javax.swing.JButton();
        jButton11 = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        jScrollPane7 = new javax.swing.JScrollPane();
        table_monitoredalarms = new javax.swing.JTable();
        jButton9 = new javax.swing.JButton();
        jButton12 = new javax.swing.JButton();
        jButton13 = new javax.swing.JButton();
        jPanel15 = new javax.swing.JPanel();
        jScrollPane9 = new javax.swing.JScrollPane();
        table_alarmAnnunciator = new javax.swing.JTable();
        jButton15 = new javax.swing.JButton();
        jButton16 = new javax.swing.JButton();
        messagesPanel = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        text_messages = new javax.swing.JTextArea();
        checkDebugSQL = new javax.swing.JCheckBox();
        checkDebugModbusConn = new javax.swing.JCheckBox();
        checkDebugJamod = new javax.swing.JCheckBox();
        jLabel24 = new javax.swing.JLabel();
        jButton6 = new javax.swing.JButton();
        viewerPanel = new javax.swing.JPanel();
        combo_viewerslave = new javax.swing.JComboBox();
        jLabel22 = new javax.swing.JLabel();
        jButton4 = new javax.swing.JButton();
        jPanel13 = new javax.swing.JPanel();
        check_viewer_auto_refresh = new javax.swing.JCheckBox();
        jScrollPane6 = new javax.swing.JScrollPane();
        table_viewer = new javax.swing.JTable();
        menuBar = new javax.swing.JMenuBar();
        javax.swing.JMenu fileMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem exitMenuItem = new javax.swing.JMenuItem();
        javax.swing.JMenu helpMenu = new javax.swing.JMenu();
        javax.swing.JMenuItem aboutMenuItem = new javax.swing.JMenuItem();

        mainPanel.setName("mainPanel"); // NOI18N
        mainPanel.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                mainPanelFocusGained(evt);
            }
        });

        mainTabbedPanel.setName("mainTabbedPanel"); // NOI18N
        mainTabbedPanel.setPreferredSize(new java.awt.Dimension(836, 400));

        mysqlPanel.setName("mysqlPanel"); // NOI18N
        mysqlPanel.setPreferredSize(new java.awt.Dimension(824, 405));

        org.jdesktop.application.ResourceMap resourceMap = org.jdesktop.application.Application.getInstance(com.enrogen.modbus2sql.Modbus2SQLApp.class).getContext().getResourceMap(MainWindow.class);
        jPanel9.setBorder(javax.swing.BorderFactory.createLineBorder(resourceMap.getColor("jPanel9.border.lineColor"))); // NOI18N
        jPanel9.setName("jPanel9"); // NOI18N

        jLabel1.setText(resourceMap.getString("jLabel1.text")); // NOI18N
        jLabel1.setName("jLabel1"); // NOI18N

        text_sqlserverip.setEditable(false);
        text_sqlserverip.setText(resourceMap.getString("text_sqlserverip.text")); // NOI18N
        text_sqlserverip.setName("text_sqlserverip"); // NOI18N

        jLabel2.setText(resourceMap.getString("jLabel2.text")); // NOI18N
        jLabel2.setName("jLabel2"); // NOI18N

        text_sqlserverport.setEditable(false);
        text_sqlserverport.setText(resourceMap.getString("text_sqlserverport.text")); // NOI18N
        text_sqlserverport.setName("text_sqlserverport"); // NOI18N

        jLabel3.setText(resourceMap.getString("jLabel3.text")); // NOI18N
        jLabel3.setName("jLabel3"); // NOI18N

        text_sqlusername.setEditable(false);
        text_sqlusername.setText(resourceMap.getString("text_sqlusername.text")); // NOI18N
        text_sqlusername.setName("text_sqlusername"); // NOI18N

        jLabel4.setText(resourceMap.getString("jLabel4.text")); // NOI18N
        jLabel4.setName("jLabel4"); // NOI18N

        jLabel5.setText(resourceMap.getString("jLabel5.text")); // NOI18N
        jLabel5.setName("jLabel5"); // NOI18N

        text_sqldatabasename.setEditable(false);
        text_sqldatabasename.setText(resourceMap.getString("text_sqldatabasename.text")); // NOI18N
        text_sqldatabasename.setName("text_sqldatabasename"); // NOI18N

        javax.swing.ActionMap actionMap = org.jdesktop.application.Application.getInstance(com.enrogen.modbus2sql.Modbus2SQLApp.class).getContext().getActionMap(MainWindow.class, this);
        btn_sqlsave.setAction(actionMap.get("saveSettingXML")); // NOI18N
        btn_sqlsave.setText(resourceMap.getString("btn_sqlsave.text")); // NOI18N
        btn_sqlsave.setActionCommand(resourceMap.getString("btn_sqlsave.actionCommand")); // NOI18N
        btn_sqlsave.setName("btn_sqlsave"); // NOI18N

        text_sqlpassword.setEditable(false);
        text_sqlpassword.setName("text_sqlpassword"); // NOI18N

        jLabel17.setText(resourceMap.getString("jLabel17.text")); // NOI18N
        jLabel17.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
        jLabel17.setName("jLabel17"); // NOI18N

        btn_sqlcancel.setAction(actionMap.get("cancelSQLSettings")); // NOI18N
        btn_sqlcancel.setText(resourceMap.getString("btn_sqlcancel.text")); // NOI18N
        btn_sqlcancel.setName("btn_sqlcancel"); // NOI18N

        btn_sqledit.setAction(actionMap.get("editSQLSettings")); // NOI18N
        btn_sqledit.setText(resourceMap.getString("btn_sqledit.text")); // NOI18N
        btn_sqledit.setName("btn_sqledit"); // NOI18N

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(btn_sqledit)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btn_sqlsave, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_sqlcancel))
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel17)
                            .addComponent(jLabel1)
                            .addComponent(jLabel2)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(text_sqlserverport)
                            .addComponent(text_sqlserverip, javax.swing.GroupLayout.DEFAULT_SIZE, 221, Short.MAX_VALUE)
                            .addComponent(text_sqlpassword)
                            .addComponent(text_sqlusername, javax.swing.GroupLayout.DEFAULT_SIZE, 221, Short.MAX_VALUE)
                            .addComponent(text_sqldatabasename))))
                .addContainerGap(21, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(jLabel17)
                .addGap(17, 17, 17)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(text_sqlserverip, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(text_sqlserverport, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(text_sqlusername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(text_sqlpassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(text_sqldatabasename, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel5))
                .addGap(18, 18, 18)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn_sqlcancel)
                    .addComponent(btn_sqlsave)
                    .addComponent(btn_sqledit))
                .addContainerGap(41, Short.MAX_VALUE))
        );

        jPanel10.setBorder(new javax.swing.border.LineBorder(resourceMap.getColor("jPanel10.border.lineColor"), 1, true)); // NOI18N
        jPanel10.setName("jPanel10"); // NOI18N

        jLabel18.setText(resourceMap.getString("jLabel18.text")); // NOI18N
        jLabel18.setName("jLabel18"); // NOI18N

        greenlamp.setIcon(resourceMap.getIcon("greenlamp.icon")); // NOI18N
        greenlamp.setText(resourceMap.getString("greenlamp.text")); // NOI18N
        greenlamp.setDisabledIcon(resourceMap.getIcon("greenlamp.disabledIcon")); // NOI18N
        greenlamp.setName("greenlamp"); // NOI18N

        redlamp.setIcon(resourceMap.getIcon("redlamp.icon")); // NOI18N
        redlamp.setDisabledIcon(resourceMap.getIcon("redlamp.disabledIcon")); // NOI18N
        redlamp.setName("redlamp"); // NOI18N

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addComponent(redlamp)
                        .addGap(12, 12, 12)
                        .addComponent(greenlamp))
                    .addComponent(jLabel18))
                .addContainerGap(94, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(jLabel18)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(redlamp)
                    .addComponent(greenlamp))
                .addContainerGap(21, Short.MAX_VALUE))
        );

        jPanel11.setBorder(javax.swing.BorderFactory.createLineBorder(resourceMap.getColor("jPanel11.border.lineColor"))); // NOI18N
        jPanel11.setName("jPanel11"); // NOI18N

        jLabel19.setText(resourceMap.getString("jLabel19.text")); // NOI18N
        jLabel19.setName("jLabel19"); // NOI18N

        jScrollPane4.setName("jScrollPane4"); // NOI18N

        jTextArea1.setBackground(resourceMap.getColor("jTextArea1.background")); // NOI18N
        jTextArea1.setColumns(20);
        jTextArea1.setEditable(false);
        jTextArea1.setRows(5);
        jTextArea1.setText(resourceMap.getString("jTextArea1.text")); // NOI18N
        jTextArea1.setBorder(null);
        jTextArea1.setName("jTextArea1"); // NOI18N
        jScrollPane4.setViewportView(jTextArea1);

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel19)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel19)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(41, Short.MAX_VALUE))
        );

        jPanel12.setBorder(new javax.swing.border.LineBorder(resourceMap.getColor("jPanel12.border.lineColor"), 1, true)); // NOI18N
        jPanel12.setName("jPanel12"); // NOI18N

        jLabel23.setText(resourceMap.getString("jLabel23.text")); // NOI18N
        jLabel23.setName("jLabel23"); // NOI18N

        btnStart.setAction(actionMap.get("btnStart")); // NOI18N
        btnStart.setText(resourceMap.getString("btnStart.text")); // NOI18N
        btnStart.setName("btnStart"); // NOI18N

        btnStop.setAction(actionMap.get("btnStop")); // NOI18N
        btnStop.setText(resourceMap.getString("btnStop.text")); // NOI18N
        btnStop.setName("btnStop"); // NOI18N

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel23)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel12Layout.createSequentialGroup()
                        .addComponent(btnStart)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnStop)))
                .addContainerGap())
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(jLabel23)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnStop)
                    .addComponent(btnStart))
                .addContainerGap())
        );

        jPanel1.setBorder(javax.swing.BorderFactory.createLineBorder(resourceMap.getColor("jPanel1.border.lineColor"))); // NOI18N
        jPanel1.setName("jPanel1"); // NOI18N

        checkStartModbusAuto.setText(resourceMap.getString("checkStartModbusAuto.text")); // NOI18N
        checkStartModbusAuto.setName("checkStartModbusAuto"); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(checkStartModbusAuto)
                .addContainerGap(185, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap(12, Short.MAX_VALUE)
                .addComponent(checkStartModbusAuto)
                .addContainerGap())
        );

        javax.swing.GroupLayout mysqlPanelLayout = new javax.swing.GroupLayout(mysqlPanel);
        mysqlPanel.setLayout(mysqlPanelLayout);
        mysqlPanelLayout.setHorizontalGroup(
            mysqlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mysqlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mysqlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jPanel11, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, mysqlPanelLayout.createSequentialGroup()
                        .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        mysqlPanelLayout.setVerticalGroup(
            mysqlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mysqlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mysqlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel9, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, mysqlPanelLayout.createSequentialGroup()
                        .addGroup(mysqlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jPanel10, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel12, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(60, Short.MAX_VALUE))
        );

        mainTabbedPanel.addTab(resourceMap.getString("mysqlPanel.TabConstraints.tabTitle"), mysqlPanel); // NOI18N

        setupPanel.setName("setupPanel"); // NOI18N
        setupPanel.setPreferredSize(new java.awt.Dimension(824, 405));
        setupPanel.addHierarchyBoundsListener(new java.awt.event.HierarchyBoundsListener() {
            public void ancestorMoved(java.awt.event.HierarchyEvent evt) {
            }
            public void ancestorResized(java.awt.event.HierarchyEvent evt) {
                resizeHandler(evt);
            }
        });

        jPanel8.setName("jPanel8"); // NOI18N
        jPanel8.setPreferredSize(new java.awt.Dimension(812, 405));

        jLabel15.setText(resourceMap.getString("jLabel15.text")); // NOI18N
        jLabel15.setName("jLabel15"); // NOI18N

        jScrollPane5.setName("jScrollPane5"); // NOI18N

        table_controllertypes.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_controllertypes.setName("table_controllertypes"); // NOI18N
        jScrollPane5.setViewportView(table_controllertypes);

        jButton1.setAction(actionMap.get("applychangesToControllerTypes")); // NOI18N
        jButton1.setText(resourceMap.getString("jButton1.text")); // NOI18N
        jButton1.setName("jButton1"); // NOI18N

        jButton7.setAction(actionMap.get("deleteControllerType")); // NOI18N
        jButton7.setText(resourceMap.getString("jButton7.text")); // NOI18N
        jButton7.setName("jButton7"); // NOI18N

        jButton8.setAction(actionMap.get("showAddControllerBox")); // NOI18N
        jButton8.setText(resourceMap.getString("jButton8.text")); // NOI18N
        jButton8.setName("jButton8"); // NOI18N

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 756, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel15)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addComponent(jButton1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jButton8, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(49, Short.MAX_VALUE))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel15)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 125, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton7)
                    .addComponent(jButton8))
                .addContainerGap(143, Short.MAX_VALUE))
        );

        setupPanel.addTab(resourceMap.getString("jPanel8.TabConstraints.tabTitle"), jPanel8); // NOI18N

        jPanel5.setName("jPanel5"); // NOI18N
        jPanel5.setPreferredSize(new java.awt.Dimension(812, 405));

        jLabel6.setText(resourceMap.getString("jLabel6.text")); // NOI18N
        jLabel6.setName("jLabel6"); // NOI18N

        jLabel7.setText(resourceMap.getString("jLabel7.text")); // NOI18N
        jLabel7.setName("jLabel7"); // NOI18N

        jLabel8.setText(resourceMap.getString("jLabel8.text")); // NOI18N
        jLabel8.setName("jLabel8"); // NOI18N

        btn_addnewPage.setAction(actionMap.get("showAddNewPageBox")); // NOI18N
        btn_addnewPage.setText(resourceMap.getString("btn_addnewPage.text")); // NOI18N
        btn_addnewPage.setName("btn_addnewPage"); // NOI18N

        jButton2.setAction(actionMap.get("delete_page")); // NOI18N
        jButton2.setText(resourceMap.getString("jButton2.text")); // NOI18N
        jButton2.setName("jButton2"); // NOI18N

        jScrollPane1.setName("jScrollPane1"); // NOI18N

        table_pages.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_pages.setName("table_pages"); // NOI18N
        jScrollPane1.setViewportView(table_pages);

        jLabel20.setText(resourceMap.getString("jLabel20.text")); // NOI18N
        jLabel20.setName("jLabel20"); // NOI18N

        jLabel21.setText(resourceMap.getString("jLabel21.text")); // NOI18N
        jLabel21.setName("jLabel21"); // NOI18N

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 727, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6)
                    .addComponent(jLabel7)
                    .addComponent(jLabel8)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(btn_addnewPage, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jButton2, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(39, 39, 39)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel21)
                            .addComponent(jLabel20))))
                .addContainerGap(78, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel6)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel7)
                .addGap(18, 18, 18)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(btn_addnewPage)
                        .addComponent(jButton2))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel20)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel21)))
                .addContainerGap(61, Short.MAX_VALUE))
        );

        setupPanel.addTab(resourceMap.getString("jPanel5.TabConstraints.tabTitle"), jPanel5); // NOI18N

        jPanel3.setName("jPanel3"); // NOI18N
        jPanel3.setPreferredSize(new java.awt.Dimension(812, 405));

        jPanel6.setName("jPanel6"); // NOI18N

        btn_apply2.setAction(actionMap.get("applychangesToSlaves")); // NOI18N
        btn_apply2.setText(resourceMap.getString("btn_apply2.text")); // NOI18N
        btn_apply2.setName("btn_apply2"); // NOI18N

        jScrollPane3.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        jScrollPane3.setName("jScrollPane3"); // NOI18N

        table_slaves.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Title 1", "Title 2", "Controller Type"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        table_slaves.setName("table_slaves"); // NOI18N
        table_slaves.setRowHeight(25);
        table_slaves.getTableHeader().setReorderingAllowed(false);
        jScrollPane3.setViewportView(table_slaves);
        table_slaves.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table_slaves.getColumnModel().getColumn(0).setResizable(false);
        table_slaves.getColumnModel().getColumn(1).setResizable(false);
        table_slaves.getColumnModel().getColumn(2).setResizable(false);

        jButton3.setAction(actionMap.get("delete_slave")); // NOI18N
        jButton3.setText(resourceMap.getString("jButton3.text")); // NOI18N
        jButton3.setName("jButton3"); // NOI18N

        btn_insertNewSlave.setAction(actionMap.get("showAddNewSlaveBox")); // NOI18N
        btn_insertNewSlave.setText(resourceMap.getString("btn_insertNewSlave.text")); // NOI18N
        btn_insertNewSlave.setName("btn_insertNewSlave"); // NOI18N

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 757, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(btn_apply2)
                        .addGap(18, 18, 18)
                        .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 147, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btn_insertNewSlave, javax.swing.GroupLayout.PREFERRED_SIZE, 136, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(11, 11, 11)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 130, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btn_apply2)
                    .addComponent(jButton3, javax.swing.GroupLayout.PREFERRED_SIZE, 27, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btn_insertNewSlave))
                .addContainerGap(20, Short.MAX_VALUE))
        );

        jLabel16.setText(resourceMap.getString("jLabel16.text")); // NOI18N
        jLabel16.setName("jLabel16"); // NOI18N

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jLabel16))
                    .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(36, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addComponent(jLabel16)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(83, Short.MAX_VALUE))
        );

        setupPanel.addTab(resourceMap.getString("jPanel3.TabConstraints.tabTitle"), jPanel3); // NOI18N

        jPanel7.setName("jPanel7"); // NOI18N
        jPanel7.setPreferredSize(new java.awt.Dimension(812, 405));

        jLabel9.setText(resourceMap.getString("jLabel9.text")); // NOI18N
        jLabel9.setName("jLabel9"); // NOI18N

        jLabel10.setText(resourceMap.getString("jLabel10.text")); // NOI18N
        jLabel10.setName("jLabel10"); // NOI18N

        combo_baud.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "9600", "19200", "57600", "115200" }));
        combo_baud.setName("combo_baud"); // NOI18N

        jLabel11.setText(resourceMap.getString("jLabel11.text")); // NOI18N
        jLabel11.setName("jLabel11"); // NOI18N

        combo_parity.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "None", "Odd", "Even" }));
        combo_parity.setName("combo_parity"); // NOI18N

        spinner_databits.setName("spinner_databits"); // NOI18N

        jLabel12.setText(resourceMap.getString("jLabel12.text")); // NOI18N
        jLabel12.setName("jLabel12"); // NOI18N

        jLabel13.setText(resourceMap.getString("jLabel13.text")); // NOI18N
        jLabel13.setName("jLabel13"); // NOI18N

        spinner_stopbits.setName("spinner_stopbits"); // NOI18N

        jLabel14.setText(resourceMap.getString("jLabel14.text")); // NOI18N
        jLabel14.setName("jLabel14"); // NOI18N

        combo_serialports.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        combo_serialports.setName("combo_serialports"); // NOI18N

        btn_saveRS485.setAction(actionMap.get("saveRS485")); // NOI18N
        btn_saveRS485.setText(resourceMap.getString("btn_saveRS485.text")); // NOI18N
        btn_saveRS485.setName("btn_saveRS485"); // NOI18N

        jButton5.setAction(actionMap.get("updatePortsCombo")); // NOI18N
        jButton5.setText(resourceMap.getString("jButton5.text")); // NOI18N
        jButton5.setName("jButton5"); // NOI18N

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel9)
                    .addComponent(jLabel10)
                    .addComponent(jLabel11)
                    .addComponent(jLabel12)
                    .addComponent(jLabel13))
                .addGap(36, 36, 36)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(spinner_stopbits, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                            .addComponent(spinner_databits, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                            .addComponent(combo_parity, 0, 140, Short.MAX_VALUE)
                            .addComponent(btn_saveRS485, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(54, 54, 54))
                    .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(combo_baud, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(combo_serialports, javax.swing.GroupLayout.Alignment.LEADING, 0, 170, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel14)
                    .addComponent(jButton5))
                .addGap(213, 213, 213))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(combo_serialports, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton5))
                .addGap(18, 18, 18)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(combo_baud, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addGap(18, 18, 18)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(combo_parity, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spinner_databits, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addGap(20, 20, 20)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(spinner_stopbits, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel13))
                .addGap(18, 18, 18)
                .addComponent(btn_saveRS485)
                .addContainerGap(47, Short.MAX_VALUE))
        );

        setupPanel.addTab(resourceMap.getString("jPanel7.TabConstraints.tabTitle"), jPanel7); // NOI18N

        jTabbedPane2.setName("jTabbedPane2"); // NOI18N

        jPanel16.setName("jPanel16"); // NOI18N

        jScrollPane8.setName("jScrollPane8"); // NOI18N

        table_alarmflags.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_alarmflags.setName("table_alarmflags"); // NOI18N
        jScrollPane8.setViewportView(table_alarmflags);

        jButton10.setAction(actionMap.get("showAddNewAlarmFlagBox")); // NOI18N
        jButton10.setText(resourceMap.getString("jButton10.text")); // NOI18N
        jButton10.setName("jButton10"); // NOI18N

        jButton11.setAction(actionMap.get("DeleteAlarmFlagBox")); // NOI18N
        jButton11.setText(resourceMap.getString("jButton11.text")); // NOI18N
        jButton11.setName("jButton11"); // NOI18N

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 805, Short.MAX_VALUE)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(jButton10, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton11, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(623, Short.MAX_VALUE))
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 204, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton10)
                    .addComponent(jButton11))
                .addContainerGap(50, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel16.TabConstraints.tabTitle"), jPanel16); // NOI18N

        jPanel14.setName("jPanel14"); // NOI18N

        jScrollPane7.setName("jScrollPane7"); // NOI18N

        table_monitoredalarms.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_monitoredalarms.setName("table_monitoredalarms"); // NOI18N
        jScrollPane7.setViewportView(table_monitoredalarms);

        jButton9.setAction(actionMap.get("showAddNewAlarmBox")); // NOI18N
        jButton9.setText(resourceMap.getString("jButton9.text")); // NOI18N
        jButton9.setName("jButton9"); // NOI18N

        jButton12.setAction(actionMap.get("DeleteAllAlarm")); // NOI18N
        jButton12.setText(resourceMap.getString("jButton12.text")); // NOI18N
        jButton12.setActionCommand(resourceMap.getString("jButton12.actionCommand")); // NOI18N
        jButton12.setName("jButton12"); // NOI18N

        jButton13.setAction(actionMap.get("DeleteAlarm")); // NOI18N
        jButton13.setText(resourceMap.getString("jButton13.text")); // NOI18N
        jButton13.setName("jButton13"); // NOI18N

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 805, Short.MAX_VALUE)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(jButton9, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(10, 10, 10)
                .addComponent(jButton13, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton12, javax.swing.GroupLayout.PREFERRED_SIZE, 340, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(175, Short.MAX_VALUE))
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 208, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton9)
                    .addComponent(jButton13)
                    .addComponent(jButton12))
                .addContainerGap(46, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel14.TabConstraints.tabTitle"), jPanel14); // NOI18N

        jPanel15.setName("jPanel15"); // NOI18N

        jScrollPane9.setName("jScrollPane9"); // NOI18N

        table_alarmAnnunciator.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_alarmAnnunciator.setName("table_alarmAnnunciator"); // NOI18N
        jScrollPane9.setViewportView(table_alarmAnnunciator);

        jButton15.setAction(actionMap.get("showAddNewAlarmAnnunciatorBox")); // NOI18N
        jButton15.setText(resourceMap.getString("jButton15.text")); // NOI18N
        jButton15.setName("jButton15"); // NOI18N

        jButton16.setAction(actionMap.get("DeleteAnnunciator")); // NOI18N
        jButton16.setText(resourceMap.getString("jButton16.text")); // NOI18N
        jButton16.setName("jButton16"); // NOI18N

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 805, Short.MAX_VALUE)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addComponent(jButton15, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton16, javax.swing.GroupLayout.PREFERRED_SIZE, 88, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(623, 623, 623))
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.PREFERRED_SIZE, 208, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton15)
                    .addComponent(jButton16))
                .addContainerGap(52, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab(resourceMap.getString("jPanel15.TabConstraints.tabTitle"), jPanel15); // NOI18N

        setupPanel.addTab(resourceMap.getString("jTabbedPane2.TabConstraints.tabTitle"), jTabbedPane2); // NOI18N

        mainTabbedPanel.addTab(resourceMap.getString("setupPanel.TabConstraints.tabTitle"), setupPanel); // NOI18N

        messagesPanel.setName("messagesPanel"); // NOI18N
        messagesPanel.setPreferredSize(new java.awt.Dimension(824, 405));

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        jScrollPane2.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        jScrollPane2.setName("jScrollPane2"); // NOI18N

        text_messages.setColumns(20);
        text_messages.setRows(5);
        text_messages.setAutoscrolls(true);
        text_messages.setName("text_messages"); // NOI18N
        jScrollPane2.setViewportView(text_messages);

        checkDebugSQL.setText(resourceMap.getString("checkDebugSQL.text")); // NOI18N
        checkDebugSQL.setName("checkDebugSQL"); // NOI18N

        checkDebugModbusConn.setText(resourceMap.getString("checkDebugModbusConn.text")); // NOI18N
        checkDebugModbusConn.setName("checkDebugModbusConn"); // NOI18N

        checkDebugJamod.setText(resourceMap.getString("checkDebugJamod.text")); // NOI18N
        checkDebugJamod.setName("checkDebugJamod"); // NOI18N

        jLabel24.setText(resourceMap.getString("jLabel24.text")); // NOI18N
        jLabel24.setName("jLabel24"); // NOI18N

        jButton6.setAction(actionMap.get("saveSettingXML")); // NOI18N
        jButton6.setText(resourceMap.getString("jButton6.text")); // NOI18N
        jButton6.setName("jButton6"); // NOI18N

        javax.swing.GroupLayout messagesPanelLayout = new javax.swing.GroupLayout(messagesPanel);
        messagesPanel.setLayout(messagesPanelLayout);
        messagesPanelLayout.setHorizontalGroup(
            messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(messagesPanelLayout.createSequentialGroup()
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 805, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(messagesPanelLayout.createSequentialGroup()
                        .addComponent(checkDebugJamod)
                        .addContainerGap(630, Short.MAX_VALUE))
                    .addGroup(messagesPanelLayout.createSequentialGroup()
                        .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(checkDebugSQL)
                            .addComponent(checkDebugModbusConn))
                        .addGap(51, 51, 51)
                        .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel24)
                            .addComponent(jButton6, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(322, 322, 322))))
        );
        messagesPanelLayout.setVerticalGroup(
            messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(messagesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 185, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkDebugSQL)
                    .addComponent(jLabel24))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(messagesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkDebugModbusConn)
                    .addComponent(jButton6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkDebugJamod)
                .addContainerGap(81, Short.MAX_VALUE))
        );

        mainTabbedPanel.addTab(resourceMap.getString("messagesPanel.TabConstraints.tabTitle"), messagesPanel); // NOI18N

        viewerPanel.setName("Viewer"); // NOI18N
        viewerPanel.setPreferredSize(new java.awt.Dimension(824, 405));

        combo_viewerslave.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        combo_viewerslave.setName("combo_viewerslave"); // NOI18N

        jLabel22.setText(resourceMap.getString("jLabel22.text")); // NOI18N
        jLabel22.setName("jLabel22"); // NOI18N

        jButton4.setAction(actionMap.get("refreshTable")); // NOI18N
        jButton4.setText(resourceMap.getString("jButton4.text")); // NOI18N
        jButton4.setName("jButton4"); // NOI18N

        jPanel13.setName("jPanel13"); // NOI18N

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 347, Short.MAX_VALUE)
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 426, Short.MAX_VALUE)
        );

        check_viewer_auto_refresh.setAction(actionMap.get("viewer_auto_refresh_check")); // NOI18N
        check_viewer_auto_refresh.setText(resourceMap.getString("check_viewer_auto_refresh.text")); // NOI18N
        check_viewer_auto_refresh.setName("check_viewer_auto_refresh"); // NOI18N

        jScrollPane6.setName("jScrollPane6"); // NOI18N

        table_viewer.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        table_viewer.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        table_viewer.setName("table_viewer"); // NOI18N
        jScrollPane6.setViewportView(table_viewer);

        javax.swing.GroupLayout viewerPanelLayout = new javax.swing.GroupLayout(viewerPanel);
        viewerPanel.setLayout(viewerPanelLayout);
        viewerPanelLayout.setHorizontalGroup(
            viewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(viewerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(viewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 763, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(viewerPanelLayout.createSequentialGroup()
                        .addGap(458, 458, 458)
                        .addComponent(jPanel13, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(viewerPanelLayout.createSequentialGroup()
                        .addGroup(viewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(viewerPanelLayout.createSequentialGroup()
                                .addComponent(combo_viewerslave, javax.swing.GroupLayout.PREFERRED_SIZE, 229, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(27, 27, 27)
                                .addComponent(jButton4))
                            .addComponent(jLabel22))
                        .addGap(18, 18, 18)
                        .addComponent(check_viewer_auto_refresh)))
                .addContainerGap())
        );
        viewerPanelLayout.setVerticalGroup(
            viewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(viewerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel22)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(viewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(combo_viewerslave, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton4)
                    .addComponent(check_viewer_auto_refresh))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 236, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(61, 61, 61)
                .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        mainTabbedPanel.addTab(resourceMap.getString("Viewer.TabConstraints.tabTitle"), viewerPanel); // NOI18N

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 841, Short.MAX_VALUE)
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainTabbedPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 424, Short.MAX_VALUE)
        );

        menuBar.setName("menuBar"); // NOI18N

        fileMenu.setText(resourceMap.getString("fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        exitMenuItem.setAction(actionMap.get("quit")); // NOI18N
        exitMenuItem.setName("exitMenuItem"); // NOI18N
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText(resourceMap.getString("helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        aboutMenuItem.setAction(actionMap.get("showAboutBox")); // NOI18N
        aboutMenuItem.setName("aboutMenuItem"); // NOI18N
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setComponent(mainPanel);
        setMenuBar(menuBar);
    }// </editor-fold>//GEN-END:initComponents

    private void resizeHandler(java.awt.event.HierarchyEvent evt) {//GEN-FIRST:event_resizeHandler
        // TODO add your handling code here:
    }//GEN-LAST:event_resizeHandler

    private void mainPanelFocusGained(
            java.awt.event.FocusEvent evt) {
    }

    //Painting
    class SpinnerEditor extends AbstractCellEditor
            implements TableCellEditor {

        final JSpinner spinner = new JSpinner();

        // Initializes the spinner.
        public SpinnerEditor(int minimum, int maximum, int step, int initialvalue) {
            SpinnerModel Spinnermodel = new SpinnerNumberModel(initialvalue,
                    minimum, //min
                    maximum, //max
                    step);                //step
            spinner.setModel(Spinnermodel);
        }

        // Prepares the spinner component and returns it.
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            spinner.setValue(value);
            return spinner;
        }

        // Returns the spinners current value.
        public Object getCellEditorValue() {
            return spinner.getValue();
        }
    }
    //////////////////////////////////////////////////////////////////////////
    //Open a TCP Port for checking alive
    //////////////////////////////////////////////////////////////////////////
    ServerSocket Modbus2SQLTCPServer = null;
    String line;
    DataInputStream is;
    PrintStream os;
    Socket clientSocket = null;
    Thread TCPThread = null;

    public void OpenTCPPort() {
        //Open the Port
        try {
            addMessage("Opening TCP Port on 25237");
            Modbus2SQLTCPServer = new ServerSocket(25237);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class TCPThreadObject extends Thread {
        // This method is called when the thread runs

        @Override
        public void run() {
            while (true) {
                //Accept data on the port in a separate Thread
                try {
                    clientSocket = Modbus2SQLTCPServer.accept();
                    is = new DataInputStream(clientSocket.getInputStream());
                    os = new PrintStream(clientSocket.getOutputStream());

                    while (true) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(is));
                        line = br.readLine();
                        String answer = getTCPAnswer(line);
                        os.println(answer);

                        //This is important... closes the TCP Connection
                        os.println("OK\n");

                        Thread.sleep(100);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // very important
                    break;
                } catch (Exception e) {
                }
            }
        }
    }

    public void startTCPThread() {
        TCPThread = new TCPThreadObject();
        TCPThread.start();
    }

    //EnrogenComm Protocol
    //"ping" = "Hi this is Enrogen Modbus2SQL Server"
    public String getTCPAnswer(String question) {
        //Default Response
        String answer = "INCORRECT SYNTAX";

        //Standard Ping
        if (question.compareToIgnoreCase("ping") == 0) {
            answer = "Hi this is Enrogen Modbus2SQL Server";
        }
        return answer;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnStart;
    private javax.swing.JButton btnStop;
    private javax.swing.JButton btn_addnewPage;
    private javax.swing.JButton btn_apply2;
    private javax.swing.JButton btn_insertNewSlave;
    private javax.swing.JButton btn_saveRS485;
    private javax.swing.JButton btn_sqlcancel;
    private javax.swing.JButton btn_sqledit;
    private javax.swing.JButton btn_sqlsave;
    private javax.swing.JCheckBox checkDebugJamod;
    private javax.swing.JCheckBox checkDebugModbusConn;
    private javax.swing.JCheckBox checkDebugSQL;
    private javax.swing.JCheckBox checkStartModbusAuto;
    private javax.swing.JCheckBox check_viewer_auto_refresh;
    private javax.swing.JComboBox combo_baud;
    private javax.swing.JComboBox combo_parity;
    private javax.swing.JComboBox combo_serialports;
    private javax.swing.JComboBox combo_viewerslave;
    private javax.swing.JLabel greenlamp;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton10;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton12;
    private javax.swing.JButton jButton13;
    private javax.swing.JButton jButton15;
    private javax.swing.JButton jButton16;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JTabbedPane mainTabbedPanel;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JPanel messagesPanel;
    private javax.swing.JPanel mysqlPanel;
    private javax.swing.JLabel redlamp;
    private javax.swing.JTabbedPane setupPanel;
    private javax.swing.JSpinner spinner_databits;
    private javax.swing.JSpinner spinner_stopbits;
    private javax.swing.JTable table_alarmAnnunciator;
    private javax.swing.JTable table_alarmflags;
    private javax.swing.JTable table_controllertypes;
    private javax.swing.JTable table_monitoredalarms;
    private javax.swing.JTable table_pages;
    private javax.swing.JTable table_slaves;
    private javax.swing.JTable table_viewer;
    private javax.swing.JTextArea text_messages;
    private javax.swing.JTextField text_sqldatabasename;
    private javax.swing.JTextField text_sqlpassword;
    private javax.swing.JTextField text_sqlserverip;
    private javax.swing.JTextField text_sqlserverport;
    private javax.swing.JTextField text_sqlusername;
    private javax.swing.JPanel viewerPanel;
    // End of variables declaration//GEN-END:variables
    private JDialog aboutBox;
    private JDialog newSlaveBox;
    private JDialog newPageBox;
    private JDialog newControllerBox;
    private JDialog newAlarmBox;
    private JDialog newAlarmFlagBox;
    private JDialog newAlarmAnnunciatorBox;
}
