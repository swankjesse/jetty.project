//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.server.session;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionTableSchemaTest
 *
 * Test the SessionTableSchema behaviour when the database treats "" as a NULL,
 * like Oracle does.
 */
public class SessionTableSchemaTest
{
    DatabaseAdaptor _da;
    JDBCSessionDataStore.SessionTableSchema _tableSchema;

    @BeforeEach
    public void setUp() throws Exception
    {
        //pretend to be an Oracle-like database that treats "" as NULL
        _da = new DatabaseAdaptor()
        {

            @Override
            public boolean isEmptyStringNull()
            {
                return true; //test special handling for oracle
            }
        };
        _da.setDriverInfo(JdbcTestHelper.DRIVER_CLASS, JdbcTestHelper.DEFAULT_CONNECTION_URL);
        _tableSchema = JdbcTestHelper.newSessionTableSchema();
        _tableSchema.setDatabaseAdaptor(_da);
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        JdbcTestHelper.shutdown(null);
    }

    @Test
    public void testLoad()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        //test if it can be seen
        try (Connection con = _da.getConnection())
        {
            //make a root context
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            //test the load statement
            PreparedStatement s = _tableSchema.getLoadStatement(con, "1234", sc);
            ResultSet rs = s.executeQuery();
            assertTrue(rs.next());
        }
    }

    @Test
    public void testExists()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        //test if it can be seen
        try (Connection con = _da.getConnection())
        {
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            PreparedStatement s = _tableSchema.getCheckSessionExistsStatement(con, sc);
            s.setString(1, "1234");
            ResultSet rs = s.executeQuery();
            assertTrue(rs.next());
        }
    }

    @Test
    public void testDelete()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        //test if it can be deleted
        try (Connection con = _da.getConnection())
        {
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            PreparedStatement s = _tableSchema.getDeleteStatement(con, "1234", sc);
            assertEquals(1, s.executeUpdate());

            assertFalse(JdbcTestHelper.existsInSessionTable("1234", false));
        }
    }

    @Test
    public void testExpired()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        try (Connection con = _da.getConnection())
        {
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            PreparedStatement s = _tableSchema.getExpiredSessionsStatement(con,
                sc.getCanonicalContextPath(),
                sc.getVhost(),
                (System.currentTimeMillis() + 100L));
            ResultSet rs = s.executeQuery();
            assertTrue(rs.next());
            assertEquals("1234", rs.getString(1));
        }
    }

    @Test
    public void testMyExpiredSessions()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        try (Connection con = _da.getConnection())
        {
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            PreparedStatement s = _tableSchema.getMyExpiredSessionsStatement(con,
                sc,
                (System.currentTimeMillis() + 100L));
            ResultSet rs = s.executeQuery();
            assertTrue(rs.next());
            assertEquals("1234", rs.getString(1));
        }
    }

    @Test
    public void testUpdate()
        throws Exception
    {
        //set up the db
        _da.initialize();
        _tableSchema.prepareTables();

        //insert a fake session at the root context
        JdbcTestHelper.insertSession("1234", "/", "0.0.0.0");

        try (Connection con = _da.getConnection())
        {
            ContextHandler handler = new ContextHandler();
            handler.setContextPath("/");
            SessionContext sc = new SessionContext("0", handler.getServletContext());
            PreparedStatement s = _tableSchema.getUpdateStatement(con,
                "1234",
                sc);

            s.setString(1, "0");//should be my node id
            s.setLong(2, System.currentTimeMillis());
            s.setLong(3, System.currentTimeMillis());
            s.setLong(4, System.currentTimeMillis());
            s.setLong(5, System.currentTimeMillis());
            s.setLong(6, 2000L);

            byte[] bytes = new byte[3];
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            s.setBinaryStream(7, bais, bytes.length);//attribute map as blob

            assertEquals(1, s.executeUpdate());
        }
    }
}
