package com.example;

/*
 * #%L
 * myHellopApp
 * %%
 * Copyright (C) 2012 - 2024 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.IOException;
import java.util.Map;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import org.json.*;

import fi.iki.elonen.NanoHTTPD;
// NOTE: If you're using NanoHTTPD >= 3.0.0 the namespace is different,
//       instead of the above import use the following:
// import org.nanohttpd.NanoHTTPD;

public class App extends NanoHTTPD {

    public App() throws IOException {
        super(8080);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    // iMariaDB [ece531]> show create table active_schedule;
    // +-----------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
    // | Table | Create Table |
    // +-----------------+----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------+
    // | active_schedule | CREATE TABLE `active_schedule` (
    // `tod` int(2) unsigned zerofill NOT NULL,
    // `temp` int(3) NOT NULL,
    // PRIMARY KEY (`tod`),
    // CONSTRAINT `tod_range` CHECK (`tod` < 24),
    // CONSTRAINT `low_temp` CHECK (`temp` > -50),
    // CONSTRAINT `high_temp` CHECK (`temp` < 100)
    // ) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci |
    // 1 row in set (0.000 sec)
    //
    private static Connection db_conn;

    public static void main(String[] args) {
        try {
            db_conn = DriverManager.getConnection("jdbc:mariadb://localhost:3306/ece531", "ece531", "REDACTED");
        } catch (Throwable exc) {
            exc.printStackTrace();
        }

        try {
            new App();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        String msg = "<html><body><h1>Thermostat Control</h1>\n";

        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();
        Map<String, String> files = new HashMap<String, String>();

// Client PUT/POST means we are inserting.  No Auth, just DO EET.
        if (Method.PUT.equals(method) || Method.POST.equals(method)) {
          try {
            session.parseBody(files);
            String json_record = files.get("postData");
            JSONObject new_record = new JSONObject(json_record);
            Integer record_id = -1;

            try {
              record_id = createData(new_record.getString("tod"), new_record.getString("temp"));
            } catch (SQLException sqle) {
                System.err.println("Error JSON inserting data:\n" + sqle);
            }
            return newFixedLengthResponse("{id: " + record_id + "}\n");
          } catch (IOException | ResponseException e) {
            System.err.println("Error parsing inbound data:\n" + e);
          }
// Client DELETE for a req of a specific ID
        } else if (Method.DELETE.equals(method)) {
          String id_req = session.getUri();
          if (id_req.substring(1).length() > 0 && !(id_req.equals("/favicon.ico"))) {
            System.out.println("Saw postData: \n" + id_req.substring(1));
            try {
              deleteData(id_req.substring(1));
            } catch (SQLException sqle) {
                System.err.println("Error deleting ID " + id_req.substring(1) + " :\n" + sqle);
            }
          }
// Client GET did a req for a specifc ID
        } else if (Method.GET.equals(method)) {
          String id_req = session.getUri();
          if (id_req.substring(1).length() > 0 && !(id_req.equals("/favicon.ico"))) {
            System.out.println("Saw postData: \n" + id_req.substring(1));
            try {
              msg = getData(id_req.substring(1));
            } catch (SQLException sqle) {
                System.err.println("Error getting ID " + id_req.substring(1) + "inserting data:\n" + sqle);
            }
          } else {
// Client is doing a GET, show data in DB.
            try {
              msg += checkData();
            } catch (SQLException sqle) {
                System.err.println("Error JSON inserting data:\n" + sqle);
            }
          }
        }
        return newFixedLengthResponse(msg);
  }

    // Grab the contents of the DB and display it so that you can see the
    // current list of peoples inserted.
    private static String checkData() throws SQLException {
      ResultSet table_contents;
      String sqlmsg = "";
    	try (PreparedStatement statement = db_conn.prepareStatement("select * from active_schedule")) {
        table_contents = statement.executeQuery();
        while (table_contents.next()) {
          sqlmsg += "tod: " + table_contents.getString("tod") + " || temp: " + table_contents.getString("temp") + "<br>";
        }
        System.out.println("Attempted to display contents: " + sqlmsg);
      } catch (Throwable exc) {
            exc.printStackTrace();
      }
      return(sqlmsg);
   }

    // Insert the data.
    private static Integer createData(String tod, String temp) throws SQLException {
      Integer new_rec_id = -1;
      try (PreparedStatement statement = db_conn.prepareStatement("REPLACE INTO active_schedule(tod, temp) VALUES (?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
        	statement.setString(1, tod);
        	statement.setString(2, temp);
        	statement.executeUpdate();

        new_rec_id = Integer.valueOf(tod); 
      } catch (Throwable exc) {
        exc.printStackTrace();
      }
      return(new_rec_id);
    }

    // Get specific ID from DB
    private static String getData(String id) throws SQLException {
      ResultSet table_contents;
      String sqlmsg = "";
    	try (PreparedStatement statement = db_conn.prepareStatement("select * from active_schedule where tod = ?")) {
        statement.setString(1, id);
        table_contents = statement.executeQuery();
        while (table_contents.next()) {
          sqlmsg += "{tod: " + table_contents.getString("tod") + ", temp: " + table_contents.getString("temp") + "}";
        }
        System.out.println("Attempted to display contents: " + sqlmsg);
      } catch (Throwable exc) {
            exc.printStackTrace();
      }
      return(sqlmsg);
  }

    // Insert the data.
    private static void deleteData(String id_req) throws SQLException {
  	try (PreparedStatement statement = db_conn.prepareStatement("delete from active_schedule where tod = ?")) {
      	statement.setString(1, id_req);
       	int rowsInserted = statement.executeUpdate();
       	System.out.println("Rows deleted: " + rowsInserted);
    } catch (Throwable exc) {
        exc.printStackTrace();
    }
  }
}
