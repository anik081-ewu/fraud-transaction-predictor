import java.sql.*;
var connection = DriverManager.getConnection("jdbc:sqlserver://localhost:1433;databaseName=fraud-transaction-detector;encrypt=true;trustServerCertificate=true", "sa", "admin");
var statement = connection.createStatement();
var columns = statement.executeQuery("SELECT t.name table_name,c.column_id,c.name column_name,ty.name data_type,c.max_length,c.precision,c.scale,c.is_nullable,CASE WHEN ic.column_id IS NULL THEN 0 ELSE 1 END identity_column FROM sys.tables t JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id LEFT JOIN sys.identity_columns ic ON ic.object_id=c.object_id AND ic.column_id=c.column_id WHERE SCHEMA_NAME(t.schema_id)='dbo' ORDER BY t.name,c.column_id");
while (columns.next()) System.out.println(String.join("|", columns.getString("table_name"), columns.getString("column_id"), columns.getString("column_name"), columns.getString("data_type"), columns.getString("max_length"), columns.getString("precision"), columns.getString("scale"), columns.getString("is_nullable"), columns.getString("identity_column")));
System.out.println("===CONFIG===");
var configs = statement.executeQuery("SELECT config_key,config_value,value_type,ISNULL(description,'') description FROM dbo.app_config ORDER BY config_key");
while (configs.next()) System.out.println(String.join("|", configs.getString(1), configs.getString(2), configs.getString(3), configs.getString(4)));
connection.close();
/exit
