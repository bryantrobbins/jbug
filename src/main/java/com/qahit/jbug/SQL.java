/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.qahit.jbug;

import java.io.Closeable;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author mosama
 */
public final class SQL implements Closeable
{

	final static AtomicInteger counter = new AtomicInteger(0);
	/**
	 * Store the database connection
	 */
	Connection dbConnection;

	public Connection getConnection()
	{
		return dbConnection;
	}

	public SQL()
	{
		try
		{
			// This synchronization together is meant to be mutually exclusive
			// with the piece of code in close. It is meant to stop one jetty
			// thread from attempting to close the database while another is trying
			// to open it here.
			synchronized (counter)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
				dbConnection = DriverManager.getConnection("jdbc:derby:jBug;create=true");
				dbConnection.setAutoCommit(false);
				counter.incrementAndGet();
				// Since we just connected, then check the DB version and upgrade if necessary
				checkDBVersion();
			}

		} catch (Exception e)
		{
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@Override
	public void close()
	{
		try
		{
			// See the comment at the constructor for informatino on why
			// to synchronize even though the counter is atomic
			synchronized (counter)
			{
				dbConnection.commit();
				dbConnection.close();

				int currentValue = counter.decrementAndGet();
				if (currentValue == 0)
				{
					// Set this value to true if you are developing. By that way
					// when netbeans deploys your touched code, jetty will reload
					// it but will close the database after every set of page views
					// and so will avoid causing derby to crash erring that the
					// db is already booted
					String close = System.getProperty("jbug.close");

					if (close != null && close.equalsIgnoreCase("true"))
					{
						DriverManager.getConnection("jdbc:derby:jBug;shutdown=true");
					}
				}
			}
		} catch (java.sql.SQLNonTransientConnectionException e)
		{
			// Do nothing.... this means successfull shutdown
		} catch (Exception e)
		{
			System.out.println("Error closing the SQL connetion:");
			e.printStackTrace();
		}
	}

	/**
	 * Checks and upgrades the database version if necessary
	 */
	void checkDBVersion() throws SQLException
	{
		// Does table vars exist ??
		try
		{
			query("select * from vars");
		} catch (Exception e)
		{
			try
			{
				System.out.println("Attempting to create the table vars");
				queryNoRes("create table vars(var varchar(32), val varchar(256))");
				queryNoRes("create index vars1 on vars(var)");
			} catch (Exception e2)
			{
				throw new RuntimeException(e2);
			}
		}

		if (getDBVersion() < 1)
		{
			upgradeToV001();
		}
	}

	/**
	 * Upgrades the database to version 001. See https://bugzilla.mozilla.org/page.cgi?id=fields.html for field
	 * definitions
	 */
	void upgradeToV001() throws SQLException
	{
		System.out.println("Upgrading to v001");

		// Main table
		queryNoRes(
		"CREATE TABLE bugs"
				+ "("
				+ "bug_id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," // Auto inc id
				+ "assigned_to VARCHAR(64) NOT NULL," // email of the assignee
				+ "status INTEGER NOT NULL,"
				+ "creation_ts BIGINT NOT NULL,"
				+ "modification_ts BIGINT NOT NULL,"
				+ "title VARCHAR(256) NOT NULL,"
				+ "description LONG VARCHAR NOT NULL,"
				+ "comments_json CLOB,"
				+ "priority INTEGER NOT NULL,"
				+ "product VARCHAR(64),"
				+ "reporter VARCHAR(64) NOT NULL,"
				+ "version VARCHAR(64),"
				+ "component VARCHAR(64),"
				+ "target_milestone VARCHAR(64),"
				+ "easiness INTEGER,"
				+ "CONSTRAINT primary_key PRIMARY KEY (bug_id)"
				+ ")");

		// Secondary indexes
		queryNoRes("create index i01 on bugs(assigned_to)");
		queryNoRes("create index i02 on bugs(status)");
		queryNoRes("create index i03 on bugs(creation_ts)");
		queryNoRes("create index i04 on bugs(modification_ts)");
		queryNoRes("create index i05 on bugs(priority)");
		queryNoRes("create index i06 on bugs(product)");
		queryNoRes("create index i08 on bugs(reporter)");
		queryNoRes("create index i09 on bugs(version)");
		queryNoRes("create index i10 on bugs(component)");
		queryNoRes("create index i12 on bugs(target_milestone)");
		queryNoRes("create index i13 on bugs(easiness)");

		setStringVar("dbversion", "1");

		System.out.println("Upgraded to V001");
		dbConnection.commit();
	}

	public ResultSet query(String sql) throws SQLException
	{
		ResultSet rs;
		Statement stmt = dbConnection.createStatement();
		rs = stmt.executeQuery(sql);
		dbConnection.commit();

		return rs;
	}

	public boolean queryNoRes(String sql) throws SQLException
	{
		boolean result;
		Statement stmt = dbConnection.createStatement();
		result = stmt.execute(sql);
		dbConnection.commit();

		return result;
	}

	public String getStringVar(String varName) throws SQLException
	{
		ResultSet rs = query("select val from vars where var='" + varName + "'");
		if (rs == null || rs.isClosed() || !rs.next())
		{
			return null;
		}
		String value = rs.getString("val");
		rs.close();
		return value;
	}

	public void setStringVar(String name, String value) throws SQLException
	{
		queryNoRes("delete from vars where var='" + name + "'");
		queryNoRes("insert into vars(var,val) values ('" + name + "','" + value + "')");
	}

	public int getDBVersion() throws SQLException
	{
		String dbVersionString = getStringVar("dbversion");
		if (dbVersionString == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(dbVersionString);
		} catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/**
	 * Converts the current row to a json string.
	 * 
	 * @param rs a ResultSet having the cursor at a certain row
	 * @return a String containing the JSON representation of the current row
	 * @throws SQLException
	 */
	static String currentRowToJSON(ResultSet rs) throws SQLException
	{
		ResultSetMetaData metaData = rs.getMetaData();
		// System.out.println("We found: " + metaData.getColumnCount() + " coulmns");
		StringBuilder b = new StringBuilder();
		for (int i = 1; i <= metaData.getColumnCount(); i++)
		{
			if (b.length() > 0)
			{
				b.append(",\n");
			}

			if (b.length() == 0)
			{
				b.append("{\n");
			}

			String columnName = metaData.getColumnName(i);
			String type = metaData.getColumnTypeName(i);
			// System.out.println("Column #" + i + ": " + columnName + " type:" + type);

			b
					.append("\"")
					.append(columnName)
					.append("\":\"");

			switch (type)
			{
				case "LONG VARCHAR" :
				case "VARCHAR" :
					String val = rs.getString(columnName);
					if (val == null)
						val = "";
					String s = val.replace("\\", "\\\\").replace("\"", "\\\"")
							.replace("\n", "\\n").replace("\r", "\\r");
					b.append((s == null) ? "" : s);
					break;

				case "INTEGER" :
					b.append(rs.getInt(columnName));
					break;

				case "BIGINT" :
					b.append(rs.getLong(columnName));
					break;

				case "CLOB" :
					Clob clob = rs.getClob(columnName);
					b.append((clob == null) ? "" : clob.toString().replace("\\", "\\\\").replace("\"", "\\\"")
							.replace("\n", "\\n").replace("\r", "\\r"));
					break;

				default :
					throw new SQLException("Unknown column type: " + type);
			}

			b.append("\"");
		}
		b.append("\n}");

		return b.toString();
	}
}
