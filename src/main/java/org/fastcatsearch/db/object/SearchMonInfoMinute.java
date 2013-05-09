/*
 * Copyright (c) 2013 Websquared, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     swsong - initial API and implementation
 */

package org.fastcatsearch.db.object;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.fastcatsearch.db.object.DAOBase;

public class SearchMonInfoMinute extends DAOBase {
	
	public int id;
	public String collection;
	public int hit;
	public int fail;
	public int achit;
	public int acfail;
	public int ave_time;
	public int max_time;
	public Timestamp when;
	
	public SearchMonInfoMinute(){}
	
	public int create() throws SQLException{
		Connection conn = null;
		Statement stmt = null;
		String createSQL = "create table "+tableName+"(id int primary key, collection varchar(20), hit int, fail int, achit int, acfail int, ave_time int, max_time int, when timestamp)";
		
		try {
			conn = conn();
			stmt = conn.createStatement();
			return stmt.executeUpdate(createSQL);
		} finally {
			releaseResource(stmt);
			releaseConnection(conn);
		}
	}
	
	public int drop() throws SQLException{
		Connection conn = null;
		Statement stmt = null;		
		String dropSQL = "drop table SearchMonInfoMinute";
		
		try{
			conn = conn();
			stmt = conn.createStatement();
			stmt.executeUpdate(dropSQL);
		} catch(SQLException e){ }
		finally {
			releaseResource(stmt);
			releaseConnection(conn);
		}
		return 0;
	}
	
	public int insert(String collection, int hit, int fail, int achit, int acfail, int ave_time, int max_time, Timestamp when) {
		Connection conn = null;
		PreparedStatement pstmt = null;
		String insertSQL = "insert into "+tableName+"(id, collection, hit, fail, achit, acfail, ave_time, max_time, when) values (?,?,?,?,?,?,?,?,?)";
		
		try{
			conn = conn();
			pstmt = conn.prepareStatement(insertSQL);
			int parameterIndex = 1;
			pstmt.setInt(parameterIndex++, ID);
			pstmt.setString(parameterIndex++, collection);
			pstmt.setInt(parameterIndex++, hit);
			pstmt.setInt(parameterIndex++, fail);
			pstmt.setInt(parameterIndex++, achit);
			pstmt.setInt(parameterIndex++, acfail);
			pstmt.setInt(parameterIndex++, ave_time);
			pstmt.setInt(parameterIndex++, max_time);
			pstmt.setTimestamp(parameterIndex++, when);
			int c =  pstmt.executeUpdate();
			if(c > 0){
				ID++;
			}
			return c;
		}catch(SQLException e){
			logger.error(e.getMessage(),e);
			return -1;
		}finally{
			releaseResource(pstmt);
			releaseConnection(conn);
		}
	}

	public int count() {
		String countSQL = "SELECT count(id) FROM "+tableName;
		Connection conn = null;
		Statement stmt = null;
		ResultSet rs = null;
		
		try{
			conn = conn();
			stmt = conn.createStatement();
			rs = stmt.executeQuery(countSQL);
			int totalCount = 0;
			if(rs.next()){
				totalCount = rs.getInt(1);
			}
			rs.close();
			stmt.close();
		
			return totalCount;
			
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
			return 0;
		} finally {
			releaseResource(stmt, rs);
			releaseConnection(conn);
		}
	}
	
	public List<SearchMonInfoMinute> select(int startRow, int length) {
		List<SearchMonInfoMinute> result = new ArrayList<SearchMonInfoMinute>();
		String countSQL = "SELECT max(id) FROM "+tableName;
		String selectSQL = "SELECT id, collection, hit, fail, achit, acfail, ave_time, max_time, when" +
				" FROM "+tableName+" WHERE id > ? and id <= ? order by id desc";
				
		Connection conn = null;
		Statement stmt = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try{
			conn = conn();
			stmt = conn.createStatement();
			rs = stmt.executeQuery(countSQL);
			
			int totalCount = 0;
			if(rs.next()){
				totalCount = rs.getInt(1);
			}
			rs.close();
			stmt.close();
		
			pstmt = conn.prepareStatement(selectSQL);
			int parameterIndex = 1;
			pstmt.setInt(parameterIndex++, totalCount - startRow - length);
			pstmt.setInt(parameterIndex++, totalCount - startRow);
			rs = pstmt.executeQuery();
			logger.debug("Start = "+(totalCount - length)+ "~"+(totalCount - startRow));
			while(rs.next()){
				SearchMonInfoMinute r = new SearchMonInfoMinute();
				parameterIndex = 1;
				r.id = rs.getInt(parameterIndex++);
				r.collection = rs.getString(parameterIndex++);
				r.hit = rs.getInt(parameterIndex++);
				r.fail = rs.getInt(parameterIndex++);
				r.achit = rs.getInt(parameterIndex++);
				r.acfail = rs.getInt(parameterIndex++);
				r.ave_time = rs.getInt(parameterIndex++);
				r.max_time = rs.getInt(parameterIndex++);
				r.when = rs.getTimestamp(parameterIndex++);
				result.add(r);
			}
			
			pstmt.close();
			rs.close();
			
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
		} finally {
			releaseResource(stmt, pstmt, rs);
			releaseConnection(conn);
		}
		
		return result;
	}
	
	public List<SearchMonInfoMinute> select(Timestamp start, Timestamp end, String collection) {
		List<SearchMonInfoMinute> result = new ArrayList<SearchMonInfoMinute>();
		String selectSQL = "SELECT id, collection, hit, fail, achit, acfail, ave_time, max_time, when" +
				" FROM "+tableName+" WHERE when >= ? and when <= ? and collection = ? order by id desc";
		
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try{
			conn = conn();
			pstmt = conn.prepareStatement(selectSQL);
			int parameterIndex = 1;
			pstmt.setTimestamp(parameterIndex++, start);
			pstmt.setTimestamp(parameterIndex++, end);
			pstmt.setString(parameterIndex++, collection);
			rs = pstmt.executeQuery();
			logger.debug(collection+" = "+start+ "~"+end);
			while(rs.next()){
				SearchMonInfoMinute r = new SearchMonInfoMinute();
				parameterIndex = 1;
				r.id = rs.getInt(parameterIndex++);
				r.collection = rs.getString(parameterIndex++);
				r.hit = rs.getInt(parameterIndex++);
				r.fail = rs.getInt(parameterIndex++);
				r.achit = rs.getInt(parameterIndex++);
				r.acfail = rs.getInt(parameterIndex++);
				r.ave_time = rs.getInt(parameterIndex++);
				r.max_time = rs.getInt(parameterIndex++);
				r.when = rs.getTimestamp(parameterIndex++);
				result.add(r);
			}
			
			pstmt.close();
			rs.close();
			
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
		} finally {
			releaseResource(pstmt, rs);
			releaseConnection(conn);
		}
		
		return result;
	}
	
	public int testAndCreate() throws SQLException {
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {
			conn = conn();
			pstmt = conn.prepareStatement("select ACHIT from "+tableName);
			rs = pstmt.executeQuery();
			rs.next();
			return 0;
		} catch (SQLException e) {
			drop();
			create();
			return 1;
		} finally {
			releaseResource(pstmt, rs);
			releaseConnection(conn);
		}
	}


	public int deleteOld(int month) {
		String deleteSQL = "Delete From "+tableName+" Where when < ?";
		
		Connection conn = null;
		PreparedStatement pstmt = null;
		
		try{
			conn = conn();
			pstmt = conn.prepareStatement(deleteSQL);
			Calendar oldDatetime = Calendar.getInstance();
			oldDatetime.set(Calendar.SECOND, 0);
			oldDatetime.set(Calendar.MINUTE, 0);
			oldDatetime.set(Calendar.HOUR, 0);
			oldDatetime.add(Calendar.MONTH, -month);
			pstmt.setTimestamp(1, new Timestamp(oldDatetime.getTimeInMillis()));
			return pstmt.executeUpdate();
		} catch(SQLException e){
			logger.error(e.getMessage(),e);
		} finally {
			releaseResource(pstmt);
			releaseConnection(conn);
		}
		return -1;
	}
	
}

