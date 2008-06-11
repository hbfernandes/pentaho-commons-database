package org.pentaho.ui.database.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import org.pentaho.di.core.database.BaseDatabaseMeta;
import org.pentaho.di.core.database.DatabaseConnectionPoolParameter;
import org.pentaho.di.core.database.DatabaseInterface;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.database.GenericDatabaseMeta;
import org.pentaho.di.core.database.PartitionDatabaseMeta;
import org.pentaho.di.core.database.SAPR3DatabaseMeta;
import org.pentaho.ui.database.Messages;
import org.pentaho.ui.util.Launch;
import org.pentaho.ui.util.Launch.Status;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.components.XulCheckbox;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMessageBox;
import org.pentaho.ui.xul.components.XulTextbox;
import org.pentaho.ui.xul.containers.XulDeck;
import org.pentaho.ui.xul.containers.XulDialog;
import org.pentaho.ui.xul.containers.XulListbox;
import org.pentaho.ui.xul.containers.XulTree;
import org.pentaho.ui.xul.containers.XulTreeItem;
import org.pentaho.ui.xul.containers.XulTreeRow;
import org.pentaho.ui.xul.containers.XulWindow;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;

/**
 * Handles all manipulation of the DatabaseMeta, data retrieval from XUL DOM and rudimentary validation.
 * 
 *  TODO:
 *  2. Needs to be abstracted away from the DatabaseMeta object, so other tools 
 *  in the platform can use the dialog and their preferred database object.
 *  3. Needs exception handling, string resourcing and logging
 *   
 * @author gmoran
 * @created Mar 19, 2008
 *
 */
public class DataHandler extends AbstractXulEventHandler {

  public static final SortedMap<String, DatabaseInterface> connectionMap = new TreeMap<String, DatabaseInterface>();

  // The connectionMap allows us to keep track of the connection
  // type we are working with and the correlating database interface

  static {
    String[] dbTypeDescriptions = DatabaseMeta.getDBTypeDescLongList();
    DatabaseInterface[] dbInterfaces = DatabaseMeta.getDatabaseInterfaces();

    // Sort the connection types, and associate them with an instance of each interface...

    for (int i = 0; i < dbTypeDescriptions.length; i++) {
      connectionMap.put(dbTypeDescriptions[i], dbInterfaces[i]);
    }
  }

  protected DatabaseMeta databaseMeta = null;

  private DatabaseMeta cache = new DatabaseMeta();

  private XulDeck dialogDeck;

  private XulListbox deckOptionsBox;

  private XulListbox connectionBox;

  private XulListbox accessBox;

  private XulTextbox connectionNameBox;

  protected XulTextbox hostNameBox;

  protected XulTextbox databaseNameBox;

  protected XulTextbox portNumberBox;

  protected XulTextbox userNameBox;

  protected XulTextbox passwordBox;

  // Generic database specific
  protected XulTextbox customDriverClassBox;

  // Generic database specific
  protected XulTextbox customUrlBox;

  // Oracle specific
  protected XulTextbox dataTablespaceBox;

  // Oracle specific
  protected XulTextbox indexTablespaceBox;

  // MS SQL Server specific
  private XulTextbox serverInstanceBox;

  // Informix specific
  private XulTextbox serverNameBox;

  // SAP R/3 specific
  protected XulTextbox languageBox;

  // SAP R/3 specific
  protected XulTextbox systemNumberBox;

  // SAP R/3 specific
  protected XulTextbox clientBox;

  // MS SQL Server specific
  private XulCheckbox doubleDecimalSeparatorCheck;

  // MySQL specific
  private XulCheckbox resultStreamingCursorCheck;

  // ==== Options Panel ==== //

  protected XulTree optionsParameterTree;

  // ==== Clustering Panel ==== //

  private XulCheckbox clusteringCheck;

  protected XulTree clusterParameterTree;

  private XulLabel clusterParameterDescriptionLabel;

  // ==== Advanced Panel ==== //

  XulCheckbox quoteIdentifiersCheck;

  XulCheckbox lowerCaseIdentifiersCheck;

  XulCheckbox upperCaseIdentifiersCheck;

  XulTextbox sqlBox;

  // ==== Pooling Panel ==== //

  private XulLabel poolSizeLabel;

  private XulLabel maxPoolSizeLabel;

  private XulCheckbox poolingCheck;

  protected XulTextbox poolSizeBox;

  protected XulTextbox maxPoolSizeBox;

  private XulTextbox poolingDescription;

  private XulLabel poolingParameterDescriptionLabel;

  private XulLabel poolingDescriptionLabel;

  protected XulTree poolParameterTree;

  public DataHandler() {
  }

  public void loadConnectionData() {

    getControls();

    // Add sorted types to the listbox now.

    for (String key : connectionMap.keySet()) {
      connectionBox.addItem(key);
    }

    // HACK: Need to force height of list control, as it does not behave 
    // well when using relative layouting

    connectionBox.setRows(connectionBox.getRows());

    Object key = connectionBox.getSelectedItem();

    // Nothing selected yet...select first item.

    // TODO Implement a connection type preference,
    // and use that type as the default for 
    // new databases.

    if (key == null) {
      key = connectionMap.firstKey();
      connectionBox.setSelectedItem(key);
    }

    // HACK: Need to force selection of first panel

    if (dialogDeck != null) {
      setDeckChildIndex();
    }

    setDefaultPoolParameters();
    // HACK: reDim the pooling table
    if(poolParameterTree != null) {
      poolParameterTree.setRows(poolParameterTree.getRows());
    }

  }

  //On Database type change
  public void loadAccessData() {

    getControls();

    pushCache();

    Object key = connectionBox.getSelectedItem();

    // Nothing selected yet...
    if (key == null) {
      key = connectionMap.firstKey();
      connectionBox.setSelectedItem(key);
      return;
    }

    DatabaseInterface database = connectionMap.get(key);

    int acc[] = database.getAccessTypeList();
    Object accessKey = accessBox.getSelectedItem();
    accessBox.removeItems();

    // Add those access types applicable to this conneciton type

    for (int value : acc) {
      accessBox.addItem(DatabaseMeta.getAccessTypeDescLong(value));
    }

    // HACK: Need to force height of list control, as it does not behave 
    // well when using relative layouting

    accessBox.setRows(accessBox.getRows());

    // May not exist for this connection type.

    accessBox.setSelectedItem(accessKey);

    // Last resort, set first as default
    if (accessBox.getSelectedItem() == null) {
      accessBox.setSelectedItem(DatabaseMeta.getAccessTypeDescLong(acc[0]));
    }

    popCache();

  }

  public void editOptions(Integer index) {
  }

  public void getOptionHelp() {

    String message = null;
    DatabaseMeta database = new DatabaseMeta();

    getInfo(database);
    String url = database.getExtraOptionsHelpText();

    if ((url == null) || (url.trim().length() == 0)) {
      message = Messages.getString("DataHandler.USER_NO_HELP_AVAILABLE"); //$NON-NLS-1$
      showMessage(message);
      return;
    }

    Status status = Launch.openURL(url);

    if (status.equals(Status.Failed)) {
      message = Messages.getString("DataHandler.USER_UNABLE_TO_LAUNCH_BROWSER", url);  //$NON-NLS-1$
      showMessage(message);
    }

  }

  public void setDeckChildIndex() {

    getControls();
    
    // if pooling selected, check the parameter validity before allowing 
    // a deck panel switch...
    int originalSelection = dialogDeck.getSelectedIndex();

    boolean passed = true;
    if (originalSelection == 3){
      passed = checkPoolingParameters();
    }
    
    if (passed) { 
      int selected = deckOptionsBox.getSelectedIndex();
      if (selected < 0) {
        selected = 0;
        deckOptionsBox.setSelectedIndex(0);
      }
      dialogDeck.setSelectedIndex(selected);
    }else{
      dialogDeck.setSelectedIndex(originalSelection);
      deckOptionsBox.setSelectedIndex(originalSelection);
    }

  }

  public void onPoolingCheck() {
    if (poolingCheck != null) {
      boolean dis = !poolingCheck.isChecked();
      if (poolSizeBox != null) {
        poolSizeBox.setDisabled(dis);
      }
      if (maxPoolSizeBox != null) {
        maxPoolSizeBox.setDisabled(dis);
      }
      if (poolSizeLabel != null) {
        poolSizeLabel.setDisabled(dis);
      }
      if (maxPoolSizeLabel != null) {
        maxPoolSizeLabel.setDisabled(dis);
      }
      if (poolParameterTree != null) {
        poolParameterTree.setDisabled(dis);
      }
      if (poolingParameterDescriptionLabel != null) {
        poolingParameterDescriptionLabel.setDisabled(dis);
      }
      if (poolingDescriptionLabel != null) {
        poolingDescriptionLabel.setDisabled(dis);
      }
      if (poolingDescription != null) {
        poolingDescription.setDisabled(dis);
      }

    }
  }

  public void onClusterCheck() {
    if (clusteringCheck != null) {
      boolean dis = !clusteringCheck.isChecked();
      if (clusterParameterTree != null) {
        clusterParameterTree.setDisabled(dis);
      }
      if(clusterParameterDescriptionLabel != null){
        clusterParameterDescriptionLabel.setDisabled(dis);
      }
    }
  }

  public Object getData() {

    if (databaseMeta == null) {
      databaseMeta = new DatabaseMeta();
    }
    
    if (!windowClosed()){
      this.getInfo(databaseMeta);
    }
    return databaseMeta;
  }

  public void setData(Object data) {
    if (data instanceof DatabaseMeta) {
      databaseMeta = (DatabaseMeta) data;
    }
    setInfo(databaseMeta);
  }

  public void pushCache() {
    getConnectionSpecificInfo(cache);
  }

  public void popCache() {
    setConnectionSpecificInfo(cache);
  }

  public void onCancel() {
    close();
  }
  
  private void close(){
  	XulComponent window = document.getElementById("general-datasource-window"); //$NON-NLS-1$
  	
  	if(window == null){ //window must be root
  		window = document.getRootElement();
  	}
    if(window instanceof XulDialog){
    	((XulDialog) window).hide();
    } else if(window instanceof XulWindow){
    	((XulWindow) window).close();
    }
  }
  
  private boolean windowClosed(){
    boolean closedWindow = true; 
    XulComponent window = document.getElementById("general-datasource-window"); //$NON-NLS-1$
    
    if(window == null){ //window must be root
      window = document.getRootElement();
    }
    if(window instanceof XulWindow){
      closedWindow =  ((XulWindow)window).isClosed();
    }
    return closedWindow;
  }

  public void onOK() {

    DatabaseMeta database = new DatabaseMeta();
    this.getInfo(database);

    boolean passed = checkPoolingParameters();
    if (!passed){
      return;
    }
    
    String[] remarks = database.checkParameters();
    String message = ""; //$NON-NLS-1$

    if (remarks.length != 0) {
      for (int i = 0; i < remarks.length; i++) {
        message = message.concat("* ").concat(remarks[i]).concat(System.getProperty("line.separator")); //$NON-NLS-1$ //$NON-NLS-2$
      }
      showMessage(message);
    } else {
      if (databaseMeta == null) {
        databaseMeta = new DatabaseMeta();
      }
      this.getInfo(databaseMeta);
      close();
    }
  }

  public void testDatabaseConnection() {

    DatabaseMeta database = new DatabaseMeta();

    getInfo(database);
    String[] remarks = database.checkParameters();
    String message = ""; //$NON-NLS-1$

    if (remarks.length != 0) {
      for (int i = 0; i < remarks.length; i++) {
        message = message.concat("* ").concat(remarks[i]).concat(System.getProperty("line.separator")); //$NON-NLS-1$  //$NON-NLS-2$
      }
    } else {
      message = database.testConnection();
    }
    showMessage(message);
  }

  protected void getInfo(DatabaseMeta meta) {

    getControls();

    if (this.databaseMeta != null && this.databaseMeta != meta) {
      meta.initializeVariablesFrom(this.databaseMeta);
    }
    // Before we put all attributes back in, clear the old list to make sure...
    // Warning: the port is an attribute too now.
    // 
    if (meta.getAttributes() != null) {
      meta.getAttributes().clear();
    }

    // Name:
    meta.setName(connectionNameBox.getValue());

    // Connection type:
    Object connection = connectionBox.getSelectedItem();
    if (connection != null) {
      meta.setDatabaseType((String) connection);
    }

    // Access type:
    Object access = accessBox.getSelectedItem();
    if (access != null) {
      meta.setAccessType(DatabaseMeta.getAccessType((String) access));
    }

    getConnectionSpecificInfo(meta);

    // Port number:
    if (portNumberBox != null) {
      meta.setDBPort(portNumberBox.getValue());
    }

    // Option parameters: 

    if (optionsParameterTree != null) {
      Object[][] values = optionsParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        String parameter = (String) values[i][0];
        String value = (String) values[i][1];

        if (value == null) {
          value = ""; //$NON-NLS-1$
        }

        int dbType = meta.getDatabaseType();

        // Only if parameter are supplied, we will add to the map...
        if ((parameter != null) && (parameter.trim().length() > 0)) {
          if (value.trim().length() <= 0) {
            value = DatabaseMeta.EMPTY_OPTIONS_STRING;
          }
          String typedParameter = BaseDatabaseMeta.ATTRIBUTE_PREFIX_EXTRA_OPTION
              + DatabaseMeta.getDatabaseTypeCode(dbType) + "." + parameter; //$NON-NLS-1$
          meta.getAttributes().put(typedParameter, value);
        }

      }
    }

    // Advanced panel settings:

    if (quoteIdentifiersCheck != null) {
      meta.setQuoteAllFields(quoteIdentifiersCheck.isChecked());
    }

    if (lowerCaseIdentifiersCheck != null) {
      meta.setForcingIdentifiersToLowerCase(lowerCaseIdentifiersCheck.isChecked());
    }

    if (upperCaseIdentifiersCheck != null) {
      meta.setForcingIdentifiersToUpperCase(upperCaseIdentifiersCheck.isChecked());
    }

    if (sqlBox != null) {
      meta.setConnectSQL(sqlBox.getValue());
    }

    // Cluster panel settings
    if (clusteringCheck != null) {
      meta.setPartitioned(clusteringCheck.isChecked());
    }

    if ((clusterParameterTree != null) && (meta.isPartitioned())) {

      Object[][] values = clusterParameterTree.getValues();
      List<PartitionDatabaseMeta> pdms = new ArrayList<PartitionDatabaseMeta>();
      for (int i = 0; i < values.length; i++) {

        String partitionId = (String) values[i][0];

        if ((partitionId == null) || (partitionId.trim().length() <= 0)) {
          continue;
        }

        String hostname = (String) values[i][1];
        String port = (String) values[i][2];
        String dbName = (String) values[i][3];
        String username = (String) values[i][4];
        String password = (String) values[i][5];
        PartitionDatabaseMeta pdm = new PartitionDatabaseMeta(partitionId, hostname, port, dbName);
        pdm.setUsername(username);
        pdm.setPassword(password);
        pdms.add(pdm);
      }
      PartitionDatabaseMeta[] pdmArray = new PartitionDatabaseMeta[pdms.size()];
      meta.setPartitioningInformation(pdms.toArray(pdmArray));
    }

    if (poolingCheck != null) {
      meta.setUsingConnectionPool(poolingCheck.isChecked());
    }

    if (meta.isUsingConnectionPool()) {
      if (poolSizeBox != null) {
        try {
          int initialPoolSize = Integer.parseInt(poolSizeBox.getValue());
          meta.setInitialPoolSize(initialPoolSize);
        } catch (NumberFormatException e) {
          // TODO log exception and move on ...
        }
      }

      if (maxPoolSizeBox != null) {
        try {
          int maxPoolSize = Integer.parseInt(maxPoolSizeBox.getValue());
          meta.setMaximumPoolSize(maxPoolSize);
        } catch (NumberFormatException e) {
          // TODO log exception and move on ...
        }
      }

      if (poolParameterTree != null) {
        Object[][] values = poolParameterTree.getValues();
        Properties properties = new Properties();
        for (int i = 0; i < values.length; i++) {

          boolean isChecked = false;
          if (values[i][0] instanceof Boolean){
            isChecked = ((Boolean)values[i][0]).booleanValue();
          }else{
            isChecked = Boolean.valueOf((String) values[i][0]);
          }

          if (!isChecked) {
            continue;
          }

          String parameter = (String) values[i][1];
          String value = (String) values[i][2];
          if ((parameter != null) && (parameter.trim().length() > 0) && (value != null) && (value.trim().length() > 0)) {
            properties.setProperty(parameter, value);
          }

        }
        meta.setConnectionPoolingProperties(properties);
      }
    }

  }

  private void setInfo(DatabaseMeta meta) {

    if (meta == null) {
      return;
    }

    getControls();

    // Name:
    connectionNameBox.setValue(meta.getName());

    // Connection type:
    connectionBox.setSelectedItem(meta.getDatabaseInterface().getDatabaseTypeDescLong());

    // Access type:
    accessBox.setSelectedItem(DatabaseMeta.getAccessTypeDescLong(meta.getAccessType()));

    // this is broken out so we can set the cache information only when caching 
    // connection values
    setConnectionSpecificInfo(meta);

    // Port number:
    if (portNumberBox != null) {
      portNumberBox.setValue(meta.getDatabasePortNumberString());
    }

    // Options Parameters:

    setOptionsData(meta.getExtraOptions());

    // Advanced panel settings:

    if (quoteIdentifiersCheck != null) {
      quoteIdentifiersCheck.setChecked(meta.isQuoteAllFields());
    }

    if (lowerCaseIdentifiersCheck != null) {
      lowerCaseIdentifiersCheck.setChecked(meta.isForcingIdentifiersToLowerCase());
    }

    if (upperCaseIdentifiersCheck != null) {
      upperCaseIdentifiersCheck.setChecked(meta.isForcingIdentifiersToUpperCase());
    }

    if (sqlBox != null) {
      sqlBox.setValue(meta.getConnectSQL() == null ? "" : meta.getConnectSQL()); //$NON-NLS-1$
    }

    // Clustering panel settings

    if (clusteringCheck != null) {
      clusteringCheck.setChecked(meta.isPartitioned());
    }

    if (meta.isPartitioned()) {
      setClusterData(meta.getPartitioningInformation());
    }

    // Pooling panel settings 

    if (poolingCheck != null) {
      poolingCheck.setChecked(meta.isUsingConnectionPool());
    }

    if (meta.isUsingConnectionPool()) {
      if (poolSizeBox != null) {
        poolSizeBox.setValue(Integer.toString(meta.getInitialPoolSize()));
      }

      if (maxPoolSizeBox != null) {
        maxPoolSizeBox.setValue(Integer.toString(meta.getMaximumPoolSize()));
      }

      setPoolProperties(meta.getConnectionPoolingProperties());
    }

    setDeckChildIndex();
    onPoolingCheck();
    onClusterCheck();
  }

  /**
   * 
   * @return the list of parameters that were enabled, but had invalid 
   * return values (null or empty)
   */
  private boolean checkPoolingParameters(){
    
    List <String> returnList = new ArrayList <String>();
    if (poolParameterTree != null) {
      Object[][] values = poolParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        boolean isChecked = false;
        if (values[i][0] instanceof Boolean){
          isChecked = ((Boolean)values[i][0]).booleanValue();
        }else{
          isChecked = Boolean.valueOf((String) values[i][0]);
        }

        if (!isChecked) {
          continue;
        }

        String parameter = (String) values[i][1];
        String value = (String) values[i][2];
        if ((value == null) || (value.trim().length() <= 0)) {
          returnList.add(parameter);
        }

      }
      if (returnList.size() > 0){
        String parameters = System.getProperty("line.separator"); //$NON-NLS-1$
        for (String parameter : returnList){
          parameters = parameters.concat(parameter).concat(System.getProperty("line.separator")); //$NON-NLS-1$
        }
        
        String message = Messages.getString("DataHandler.USER_INVALID_PARAMETERS").concat(parameters); //$NON-NLS-1$
        showMessage(message);
      }
    }
    return returnList.size() <= 0;
  }

  private void setPoolProperties(Properties properties) {
    if (poolParameterTree != null) {
      Object[][] values = poolParameterTree.getValues();
      for (int i = 0; i < values.length; i++) {

        String parameter = (String) values[i][1];
        boolean isChecked = properties.containsKey(parameter);

        if (!isChecked) {
          continue;
        }
        XulTreeItem item = poolParameterTree.getRootChildren().getItem(i);
        item.getRow().addCellText(0, "true"); // checks the checkbox //$NON-NLS-1$

        String value = properties.getProperty(parameter);
        item.getRow().addCellText(2, value);

      }
    }

  }
  
  public void restoreDefaults(){
    if (poolParameterTree != null) {
      for (int i = 0; i < poolParameterTree.getRootChildren().getItemCount(); i++){
        XulTreeItem item = poolParameterTree.getRootChildren().getItem(i);
        String parameterName = item.getRow().getCell(1).getLabel();
        String defaultValue = DatabaseConnectionPoolParameter.findParameter(parameterName, BaseDatabaseMeta.poolingParameters).getDefaultValue();
        if ((defaultValue == null) || (defaultValue.trim().length()<=0)){
          continue;
        }
        item.getRow().addCellText(2, defaultValue);
      }
    }
    
  }

  private void setDefaultPoolParameters() {
    if (poolParameterTree != null) {
      for (DatabaseConnectionPoolParameter parameter : BaseDatabaseMeta.poolingParameters){
        XulTreeRow row = poolParameterTree.getRootChildren().addNewRow();
        row.addCellText(0, "false"); //$NON-NLS-1$
        row.addCellText(1, parameter.getParameter());
        row.addCellText(2, parameter.getDefaultValue());
      }
    }
  }

  private void setOptionsData(Map<String, String> extraOptions) {

    if (optionsParameterTree != null) {
      Iterator<String> keys = extraOptions.keySet().iterator();
      while (keys.hasNext()) {

        String parameter = keys.next();
        String value = extraOptions.get(parameter);
        if ((value == null) || (value.trim().length() <= 0) || (value.equals(DatabaseMeta.EMPTY_OPTIONS_STRING))) {
          value = ""; //$NON-NLS-1$
        }

        // If the parameter starts with a database type code we add it...
        // For example MySQL.defaultFetchSize

        int dotIndex = parameter.indexOf('.');
        if (dotIndex >= 0) {
          String parameterOption = parameter.substring(dotIndex + 1);

          XulTreeRow row = optionsParameterTree.getRootChildren().addNewRow();
          row.addCellText(0, parameterOption);
          row.addCellText(1, value);

        }
      }
    }
  }

  private void setClusterData(PartitionDatabaseMeta[] clusterInformation) {

    if ((clusterInformation != null) && (clusterParameterTree != null)) {

      for (int i = 0; i < clusterInformation.length; i++) {

        PartitionDatabaseMeta meta = clusterInformation[i];
        XulTreeRow row = clusterParameterTree.getRootChildren().addNewRow();
        row.addCellText(0, meta.getPartitionId() == null ? "" : meta.getPartitionId()); //$NON-NLS-1$
        row.addCellText(1, meta.getHostname() == null ? "" : meta.getHostname()); //$NON-NLS-1$
        row.addCellText(2, meta.getPort() == null ? "" : meta.getPort()); //$NON-NLS-1$
        row.addCellText(3, meta.getDatabaseName() == null ? "" : meta.getDatabaseName()); //$NON-NLS-1$
        row.addCellText(4, meta.getUsername() == null ? "" : meta.getUsername()); //$NON-NLS-1$
        row.addCellText(5, meta.getPassword() == null ? "" : meta.getPassword()); //$NON-NLS-1$
      }
    }
  }

  public void poolingRowChange(Integer idx) {

    if (idx != -1) {

      if (idx >= BaseDatabaseMeta.poolingParameters.length) {
        idx = BaseDatabaseMeta.poolingParameters.length - 1;
      }
      if (idx < 0) {
        idx = 0;
      }
      poolingDescription.setValue(BaseDatabaseMeta.poolingParameters[idx].getDescription());
      
      XulTreeRow row = poolParameterTree.getRootChildren().getItem(idx).getRow();
      if (row.getSelectedColumnIndex() == 2){
        row.addCellText(0, "true"); //$NON-NLS-1$
      }
      
    }
  }

  private void getConnectionSpecificInfo(DatabaseMeta meta) {
    // Hostname:
    if (hostNameBox != null) {
      meta.setHostname(hostNameBox.getValue());
    }

    // Database name:
    if (databaseNameBox != null) {
      meta.setDBName(databaseNameBox.getValue());
    }

    // Username:
    if (userNameBox != null) {
      meta.setUsername(userNameBox.getValue());
    }

    // Password:
    if (passwordBox != null) {
      meta.setPassword(passwordBox.getValue());
    }

    // Streaming result cursor:
    if (resultStreamingCursorCheck != null) {
      meta.setStreamingResults(resultStreamingCursorCheck.isChecked());
    }

    // Data tablespace:
    if (dataTablespaceBox != null) {
      meta.setDataTablespace(dataTablespaceBox.getValue());
    }

    // Index tablespace
    if (indexTablespaceBox != null) {
      meta.setIndexTablespace(indexTablespaceBox.getValue());
    }

    // The SQL Server instance name overrides the option.
    // Empty doesn't clear the option, we have mercy.

    if (serverInstanceBox != null) {
      if (serverInstanceBox.getValue().trim().length() > 0) {
        meta.setSQLServerInstance(serverInstanceBox.getValue());
      }
    }

    // SQL Server double decimal separator
    if (doubleDecimalSeparatorCheck != null) {
      meta.setUsingDoubleDecimalAsSchemaTableSeparator(doubleDecimalSeparatorCheck.isChecked());
    }

    // SAP Attributes...
    if (languageBox != null) {
      meta.getAttributes().put(SAPR3DatabaseMeta.ATTRIBUTE_SAP_LANGUAGE, languageBox.getValue());
    }
    if (systemNumberBox != null) {
      meta.getAttributes().put(SAPR3DatabaseMeta.ATTRIBUTE_SAP_SYSTEM_NUMBER, systemNumberBox.getValue());
    }
    if (clientBox != null) {
      meta.getAttributes().put(SAPR3DatabaseMeta.ATTRIBUTE_SAP_CLIENT, clientBox.getValue());
    }

    // Generic settings...
    if (customUrlBox != null) {
      meta.getAttributes().put(GenericDatabaseMeta.ATRRIBUTE_CUSTOM_URL, customUrlBox.getValue());
    }
    if (customDriverClassBox != null) {
      meta.getAttributes().put(GenericDatabaseMeta.ATRRIBUTE_CUSTOM_DRIVER_CLASS, customDriverClassBox.getValue());
    }

    // Server Name:  (Informix)
    if (serverNameBox != null) {
      meta.setServername(serverNameBox.getValue());
    }

  }

  private void setConnectionSpecificInfo(DatabaseMeta meta) {

    getControls();

    if (hostNameBox != null) {
      hostNameBox.setValue(meta.getHostname());
    }

    // Database name:
    if (databaseNameBox != null) {
      databaseNameBox.setValue(meta.getDatabaseName());
    }

    // Username:
    if (userNameBox != null) {
      userNameBox.setValue(meta.getUsername());
    }

    // Password:
    if (passwordBox != null) {
      passwordBox.setValue(meta.getPassword());
    }

    // Streaming result cursor:
    if (resultStreamingCursorCheck != null) {
      resultStreamingCursorCheck.setChecked(meta.isStreamingResults());
    }

    // Data tablespace:
    if (dataTablespaceBox != null) {
      dataTablespaceBox.setValue(meta.getDataTablespace());
    }

    // Index tablespace
    if (indexTablespaceBox != null) {
      indexTablespaceBox.setValue(meta.getIndexTablespace());
    }

    if (serverInstanceBox != null) {
      serverInstanceBox.setValue(meta.getSQLServerInstance());
    }

    // SQL Server double decimal separator
    if (doubleDecimalSeparatorCheck != null) {
      doubleDecimalSeparatorCheck.setChecked(meta.isUsingDoubleDecimalAsSchemaTableSeparator());
    }

    // SAP Attributes...
    if (languageBox != null) {
      languageBox.setValue(meta.getAttributes().getProperty(SAPR3DatabaseMeta.ATTRIBUTE_SAP_LANGUAGE));
    }
    if (systemNumberBox != null) {
      systemNumberBox.setValue(meta.getAttributes().getProperty(SAPR3DatabaseMeta.ATTRIBUTE_SAP_SYSTEM_NUMBER));
    }
    if (clientBox != null) {
      clientBox.setValue(meta.getAttributes().getProperty(SAPR3DatabaseMeta.ATTRIBUTE_SAP_CLIENT));
    }

    // Generic settings...
    if (customUrlBox != null) {
      customUrlBox.setValue(meta.getAttributes().getProperty(GenericDatabaseMeta.ATRRIBUTE_CUSTOM_URL));
    }
    if (customDriverClassBox != null) {
      customDriverClassBox
          .setValue(meta.getAttributes().getProperty(GenericDatabaseMeta.ATRRIBUTE_CUSTOM_DRIVER_CLASS));
    }

    // Server Name:  (Informix)
    if (serverNameBox != null) {
      serverNameBox.setValue(meta.getServername());
    }

  }

  protected void getControls() {

    // Not all of these controls are created at the same time.. that's OK, for now, just check
    // each one for null before using.

    dialogDeck = (XulDeck) document.getElementById("dialog-panel-deck"); //$NON-NLS-1$
    deckOptionsBox = (XulListbox) document.getElementById("deck-options-list"); //$NON-NLS-1$
    connectionBox = (XulListbox) document.getElementById("connection-type-list"); //$NON-NLS-1$
    accessBox = (XulListbox) document.getElementById("access-type-list"); //$NON-NLS-1$
    connectionNameBox = (XulTextbox) document.getElementById("connection-name-text"); //$NON-NLS-1$
    hostNameBox = (XulTextbox) document.getElementById("server-host-name-text"); //$NON-NLS-1$
    databaseNameBox = (XulTextbox) document.getElementById("database-name-text"); //$NON-NLS-1$
    portNumberBox = (XulTextbox) document.getElementById("port-number-text"); //$NON-NLS-1$
    userNameBox = (XulTextbox) document.getElementById("username-text"); //$NON-NLS-1$
    passwordBox = (XulTextbox) document.getElementById("password-text"); //$NON-NLS-1$
    dataTablespaceBox = (XulTextbox) document.getElementById("data-tablespace-text"); //$NON-NLS-1$
    indexTablespaceBox = (XulTextbox) document.getElementById("index-tablespace-text"); //$NON-NLS-1$
    serverInstanceBox = (XulTextbox) document.getElementById("instance-text"); //$NON-NLS-1$
    serverNameBox = (XulTextbox) document.getElementById("server-name-text"); //$NON-NLS-1$
    customUrlBox = (XulTextbox) document.getElementById("custom-url-text"); //$NON-NLS-1$
    customDriverClassBox = (XulTextbox) document.getElementById("custom-driver-class-text"); //$NON-NLS-1$
    languageBox = (XulTextbox) document.getElementById("language-text"); //$NON-NLS-1$
    systemNumberBox = (XulTextbox) document.getElementById("system-number-text"); //$NON-NLS-1$
    clientBox = (XulTextbox) document.getElementById("client-text"); //$NON-NLS-1$
    doubleDecimalSeparatorCheck = (XulCheckbox) document.getElementById("decimal-separator-check"); //$NON-NLS-1$
    resultStreamingCursorCheck = (XulCheckbox) document.getElementById("result-streaming-check"); //$NON-NLS-1$
    poolingCheck = (XulCheckbox) document.getElementById("use-pool-check"); //$NON-NLS-1$
    clusteringCheck = (XulCheckbox) document.getElementById("use-cluster-check"); //$NON-NLS-1$
    clusterParameterDescriptionLabel = (XulLabel) document.getElementById("cluster-parameter-description-label"); //$NON-NLS-1$
    poolSizeLabel = (XulLabel) document.getElementById("pool-size-label"); //$NON-NLS-1$
    poolSizeBox = (XulTextbox) document.getElementById("pool-size-text"); //$NON-NLS-1$
    maxPoolSizeLabel = (XulLabel) document.getElementById("max-pool-size-label"); //$NON-NLS-1$
    maxPoolSizeBox = (XulTextbox) document.getElementById("max-pool-size-text"); //$NON-NLS-1$
    poolParameterTree = (XulTree) document.getElementById("pool-parameter-tree"); //$NON-NLS-1$
    clusterParameterTree = (XulTree) document.getElementById("cluster-parameter-tree"); //$NON-NLS-1$
    optionsParameterTree = (XulTree) document.getElementById("options-parameter-tree"); //$NON-NLS-1$
    poolingDescription = (XulTextbox) document.getElementById("pooling-description"); //$NON-NLS-1$ 
    poolingParameterDescriptionLabel = (XulLabel) document.getElementById("pool-parameter-description-label"); //$NON-NLS-1$ 
    poolingDescriptionLabel = (XulLabel) document.getElementById("pooling-description-label"); //$NON-NLS-1$ 
    quoteIdentifiersCheck = (XulCheckbox) document.getElementById("quote-identifiers-check"); //$NON-NLS-1$;
    lowerCaseIdentifiersCheck = (XulCheckbox) document.getElementById("force-lower-case-check"); //$NON-NLS-1$;
    upperCaseIdentifiersCheck = (XulCheckbox) document.getElementById("force-upper-case-check"); //$NON-NLS-1$;
    sqlBox = (XulTextbox) document.getElementById("sql-text"); //$NON-NLS-1$;
  }

  private void showMessage(String message){
    try{
      XulMessageBox box = (XulMessageBox) document.createElement("messagebox"); //$NON-NLS-1$
      box.setMessage(message);
      box.open();
    } catch(XulException e){
      System.out.println("Error creating messagebox "+e.getMessage());
    }
  }
}
