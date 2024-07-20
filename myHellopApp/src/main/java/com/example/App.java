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

import fi.iki.elonen.NanoHTTPD;
// NOTE: If you're using NanoHTTPD >= 3.0.0 the namespace is different,
//       instead of the above import use the following:
// import org.nanohttpd.NanoHTTPD;

public class App extends NanoHTTPD {

    public App() throws IOException {
        super(8080);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browsers to http://localhost:8080/ \n");
    }

    // Current database schema:
    // MariaDB [ece531]> show tables ;
    // +------------------+
    // | Tables_in_ece531 |
    // +------------------+
    // | active_users |
    // +------------------+
    // 1 row in set (0.000 sec)
    //
    // MariaDB [ece531]> describe active_users;
    // +-------+------------------+------+-----+---------+----------------+
    // | Field | Type | Null | Key | Default | Extra |
    // +-------+------------------+------+-----+---------+----------------+
    // | id | int(10) unsigned | NO | PRI | NULL | auto_increment |
    // | fname | varchar(150) | NO | | NULL | |
    // | lname | varchar(150) | NO | | NULL | |
    // +-------+------------------+------+-----+---------+----------------+
    // 3 rows in set (0.001 sec)

    private static Connection db_conn;

    public static void main(String[] args) {
        try {
            db_conn = DriverManager.getConnection("jdbc:mariadb://localhost:3306/ece531", "ece531", "REDACT");
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
        String msg = "<html><body><h1>Hello server</h1>\n";
        Map<String, String> parms = session.getParms();
        if (parms.get("username") == null) {
            msg += "<form action='?' method='get'>";
            msg += "<input type='text' name='username'>";
	    msg += "<label for='username'> Enter your new username here</label>";
	    msg += "<br>";
            msg += "<input type='text' name='username2'>";
	    msg += "<label for='username2'> Enter your new username2 here</label>";
            msg += "</form>";
        } else {
            msg += "<p>Hello, " + parms.get("username") + "!</p>";
            try {
                createData(parms.get("username"), "test");
            } catch (SQLException sqle) {
                System.err.println("Error inserting data:\n" + sqle);
            }
        }
        return newFixedLengthResponse(msg + "</body></html>\n");
    }

    private static void createData(String fname, String lname) throws SQLException {
    	try (PreparedStatement statement = db_conn.prepareStatement("INSERT INTO active_users(fname, lname) VALUES (?, ?)")) {
        	statement.setString(1, fname);
        	statement.setString(2, lname);
        	int rowsInserted = statement.executeUpdate();
        	System.out.println("Rows inserted: " + rowsInserted);
        } catch (Throwable exc) {
            exc.printStackTrace();
        }
	}
}
