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

package org.fastcatsearch.datasource.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.fastcatsearch.datasource.SourceModifier;
import org.fastcatsearch.env.Path;
import org.fastcatsearch.ir.common.IRException;
import org.fastcatsearch.ir.config.CollectionStatus.IndexStatus;
import org.fastcatsearch.ir.config.DBSourceConfig;
import org.fastcatsearch.ir.document.Document;
import org.fastcatsearch.ir.field.Field;
import org.fastcatsearch.ir.index.DeleteIdSet;
import org.fastcatsearch.ir.index.PrimaryKeys;
import org.fastcatsearch.ir.settings.FieldSetting;
import org.fastcatsearch.ir.settings.Schema;
import org.fastcatsearch.ir.util.Formatter;
import org.fastcatsearch.util.DynamicClassLoader;
import org.fastcatsearch.util.HTMLTagRemover;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DBReader extends SingleSourceReader {
	
	private static Logger logger = LoggerFactory.getLogger(DBReader.class);
	private int BULK_SIZE;
	
	private Connection con;
	private PreparedStatement pstmt;
	private ResultSet r;
//	private Object[][] fieldSet;
	private int columnCount;
	private String[] columnName;
	private Map<String, Object>[] dataSet;
	
	private int bulkCount;
	private int readCount;
	private boolean isFull;
	
	boolean useBackup;
	private BufferedWriter backupWriter;
	private String startTime;
	private String deleteFileName;
	private DBSourceConfig config;
	private String lastIndexTime;
	
	public DBReader(File filePath, DBSourceConfig config, String lastIndexTime, boolean isFull) throws IRException {
		super(filePath, config, lastIndexTime, isFull);
		this.config = config;
		this.isFull = isFull;
		this.BULK_SIZE = config.getBulkSize();
		this.startTime = Formatter.getFormatTime(System.currentTimeMillis());
		
//		fieldSet = new Object[BULK_SIZE][];
		dataSet = new Map[BULK_SIZE];
		
		try{
			if(config.getJdbcDriver() != null && config.getJdbcDriver().length() > 0){
				Object object = DynamicClassLoader.loadObject(config.getJdbcDriver());
				if(object == null){
					throw new IRException("Cannot find sql driver = "+config.getJdbcDriver());
				}else{
					Driver driver = (Driver)object;
					DriverManager.registerDriver(driver);
					Properties info = new Properties();
					info.put("user", config.getJdbcUser());
					info.put("password", config.getJdbcPassword());
					con = driver.connect(config.getJdbcUrl(), info);
					con.setAutoCommit(true);
				}
			}else{
				throw new IRException("JDBC driver is empty!");
			}
			if(sourceModifier!=null) {
				sourceModifier.init(con);
			}
			doBeforeQuery();
			
			if(isFull){
				logger.debug("Full query = "+q(config.getFullQuery()));
				if(config.getFullQuery() == null || config.getFullQuery().length() == 0){
					throw new IRException("Full query sql is empty!");
				}
				
				if ( config.getFetchSize() <= 0 )	
					pstmt = con.prepareStatement(q(config.getFullQuery()), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
				else
					pstmt = con.prepareStatement(q(config.getFullQuery()));
				
				useBackup = (config.getFullBackupPath() != null && config.getFullBackupPath().length() > 0);
				if(useBackup){
					backupWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filePath, config.getFullBackupPath())), config.getBackupFileEncoding()));
					deleteFileName = config.getFullBackupPath()+".delete";
				}
			}else{
				if(config.getDeleteIdQuery() != null && config.getDeleteIdQuery().length() > 0){
					PreparedStatement idPstmt = con.prepareStatement(q(config.getDeleteIdQuery()));
					ResultSet rs = idPstmt.executeQuery();
					while(rs.next()){
						String ID = rs.getString(1);
						deleteIdList.add(ID);
					}
					idPstmt.close();
				}
				logger.debug("Add query = "+q(config.getIncQuery()));
				if(config.getIncQuery() == null || config.getIncQuery().length() == 0){
					throw new IRException("Incremental query sql is empty!");
				}
				pstmt = con.prepareStatement(q(config.getIncQuery()));
				useBackup = (config.getIncBackupPath() != null && config.getIncBackupPath().length() > 0);
				if(useBackup){
					backupWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filePath, config.getIncBackupPath())), config.getBackupFileEncoding()));
					deleteFileName = config.getIncBackupPath()+".delete";
				}
			}
			
			if(config.isResultBuffering()) {
				pstmt = new BufferedStatement(pstmt);
			}
			
			if(config.getFetchSize() > 0){
				pstmt.setFetchSize(config.getFetchSize());
			}else if(config.getFetchSize() <= 0){
				pstmt.setFetchSize(Integer.MIN_VALUE);
			}
			r = pstmt.executeQuery();
			
			ResultSetMetaData rsMetadata = r.getMetaData();
			columnCount = rsMetadata.getColumnCount();
			columnName = new String[columnCount];
			for (int i = 0; i < columnCount; i++) {
				columnName[i] = rsMetadata.getColumnName(i + 1).toLowerCase();
				String typeName = rsMetadata.getColumnTypeName(i + 1);
				logger.info("Column-{} [{}]:[{}]", new Object[]{i+1, columnName[i], typeName});
			}
		} catch (Exception e) {
			try{
				if (r != null) r.close();
			} catch (SQLException e1) { }

			try{
				if (pstmt != null) pstmt.close();
			} catch (SQLException e1) { }

			try{
				if (con != null && !con.isClosed()) con.close();
			} catch (SQLException e1) { }
			
			try{
				if (backupWriter != null) backupWriter.close();
			} catch (IOException e1) { }
			
			throw new IRException(e);
		}
			
	}
	
	private String q(String query){
		if(lastIndexTime != null){
			if(lastIndexTime.length() == 0){
				//현재시각으로 넣어준다.
				return query.replaceAll("\\$\\{last_index_time\\}", "'"+Formatter.formatDate()+"'");
			}else{
				return query.replaceAll("\\$\\{last_index_time\\}", "'"+lastIndexTime+"'");
			}
		}
			
		return query;
	}
	
	@Override
	public void close() throws IRException{
		logger.info("DBReader has read {} docs", totalCnt);
		try {
			doAfterQuery();
		} catch (SQLException e) { 
			logger.error("After Query Error => "+e.getMessage(),e);
		}
			
		try{
			if (r != null) r.close();
		} catch (SQLException e) { }

		try{
			if (pstmt != null) pstmt.close();
		} catch (SQLException e) { }

		try{
			if (con != null && !con.isClosed()) con.close();
		} catch (SQLException e) { }
		try{
			if (backupWriter != null) backupWriter.close();
		} catch (IOException e) { }
		
		//write delete doc list
		if(useBackup){
			try{
				BufferedWriter deleteBackupWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(filePath, deleteFileName)), config.getBackupFileEncoding()));
				Iterator<PrimaryKeys> iter = deleteIdList.iterator();
				
				while(iter.hasNext()){
					deleteBackupWriter.write(iter.next().toString());
					deleteBackupWriter.newLine();
				}
				
				deleteBackupWriter.close();
			} catch (IOException e) { }
		}
		
	}
	private int executeUpdateQuery(String query) throws SQLException {
		if (query == null || query.length() == 0)
			return -1;
		logger.debug("Execute Update SQL = {}", query);
		PreparedStatement pstmt = con.prepareStatement(query);
		int count = pstmt.executeUpdate();
		pstmt.close();
		return count;
	}
	private void doBeforeQuery() throws SQLException{
		int count = -1;
		if(isFull)
			count = executeUpdateQuery(q(config.getBeforeFullQuery()));
		else 
			count = executeUpdateQuery(q(config.getBeforeIncQuery()));
		
		if(count != -1)
			logger.info("Before query updated "+count+" rows.");
	}
	private void doAfterQuery() throws SQLException{
		int count = -1;
		if(isFull)
			count = executeUpdateQuery(q(config.getAfterFullQuery()));
		else
			count = executeUpdateQuery(q(config.getAfterIncQuery()));
		
		if(count != -1)
			logger.info("After query updated "+count+" rows.");
	}
	
	@Override
	public boolean hasNext() throws IRException{
		if(readCount >= bulkCount){
			fill();
			
			if(bulkCount == 0)
				return false;
			
			readCount = 0;
		}
		return true;
	}
	
	@Override
	protected final Map<String, Object> next() throws IRException {
		if(readCount >= bulkCount){
			fill();
			if(bulkCount == 0)
				return null;
			readCount = 0;
		}
		
//		Document document = new Document(fieldSettingList.size());
//		for (int i = 0; i < fieldSettingList.size(); i++) {
//			FieldSetting fs = fieldSettingList.get(i);
//			String data = "";
////			if(!fs.isBlob())
//			
//			data = (String) fieldSet[readCount][i];
//			
////			logger.debug("read data="+data+", readCount="+readCount+", i="+i);
//			Field f = fs.createField(data);
//			document.set(i,  f);
//		}
//		readCount++;
		return dataSet[readCount++];
	}
	
	byte[] data = new byte[16 * 1024];
	int totalCnt = 0;
	
	private void fill() throws IRException {
		bulkCount = 0;
		try{
			
			Map<String,Object> keyValueMap = new HashMap<String,Object>();
			
			ResultSetMetaData rsMeta = null;
		
			try {
				rsMeta = r.getMetaData();
			} catch (SQLException e) {
				return;
			}
			
			while (r.next()){
				boolean hasLob = false;
				for (int i = 0; i < columnCount; i++) {
					int columnIdx = i+1;
					int type = rsMeta.getColumnType(columnIdx);
					
					String str = r.getString(columnIdx);
					if(str == null) {
						if(type == Types.BLOB || type == Types.BINARY || type == Types.LONGVARBINARY || type == Types.VARBINARY || type == Types.JAVA_OBJECT){
							//logger.debug("Column-"+columnIdx+" is BLOB!");
							//BLOB일 경우 스트림으로 받는다.
							File f = null;
							FileOutputStream os = null;
							InputStream is = null;
							try {
								f = File.createTempFile("blob."+columnIdx, ".tmp");
								//logger.debug("tmp file = "+f.getAbsolutePath());
								is = r.getBinaryStream(columnIdx);
								if (is != null) {
									os = new FileOutputStream(f);
									for(int rlen=0;(rlen = is.read(data, 0, data.length)) != -1;) { os.write(data, 0, rlen); }

									keyValueMap.put(columnName[i], f);
									hasLob = true;
								}else{
									keyValueMap.put(columnName[i], "");
								}
								
							} catch (IOException e) {
								throw new IRException("Error while writing Blob field. column => "+rsMeta.getColumnName(columnIdx));
							} finally {
								IOException ex = null;
								if(os!=null) try { os.close(); } catch (IOException e) { ex = e; }
								if(is!=null) try { is.close(); } catch (IOException e) { ex = e; }
								if(ex!=null) { logger.error("Error while close blob field and output file stream.",ex); }
							}
						} else if(type == Types.CLOB) {
							
							File f = null;
							BufferedWriter os = null;
							BufferedReader is = null;
							try {
								f = File.createTempFile("clob."+columnIdx, ".tmp");
								Reader reader = r.getCharacterStream(columnIdx);
								if(reader != null){
									is = new BufferedReader(reader);
									os = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
									while ((str = is.readLine())!=null) { os.write(str); }
									keyValueMap.put(columnName[i], f);
									hasLob = true;
								}else{
									keyValueMap.put(columnName[i], "");
								}
							} catch (IOException e) {
								throw new IRException("Error while writing Clob field. column => "+rsMeta.getColumnName(columnIdx));
							} finally {
								IOException ex = null;
								if(os!=null) try { os.close(); } catch (IOException e) { ex = e; }
								if(is!=null) try { is.close(); } catch (IOException e) { ex = e; }
								if(f!=null && f.exists()) { f.delete(); }
								if(ex!=null) { logger.error("Error while close clob field and output file stream.",ex); }
							}
						} else {
							//java 1.6 이상지원 jdbc4.0 CLOB 필드
							// CLOB과 동일하게 처리해준다.
							// java1.5의 경우는 위에서 처리가 되며,
							// 데이터가 null이거나 java1.6이상의 CLOB데이터는 이곳으로 넘어오게 되는데, 에러가 발생하면 null로 처리해준다.  
							try{
								if(type == Types.NCLOB || type == Types.SQLXML || type==Types.LONGVARCHAR || type==Types.LONGNVARCHAR){
									File f = null;
									BufferedWriter os = null;
									BufferedReader is = null;
									try {
										f = File.createTempFile("clob."+columnIdx, ".tmp");
										Reader reader = r.getCharacterStream(columnIdx);
										if(reader != null){
											is = new BufferedReader(reader);
											os = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
											while ((str = is.readLine())!=null) { os.write(str); }
											keyValueMap.put(columnName[i], f);
											hasLob = true;
										}else{
											keyValueMap.put(columnName[i], "");
										}
									} catch (IOException e) {
										throw new IRException("Error while writing Clob field. column => "+rsMeta.getColumnName(columnIdx));
									} finally {
										IOException ex = null;
										if(os!=null) try { os.close(); } catch (IOException e) { ex = e; }
										if(is!=null) try { is.close(); } catch (IOException e) { ex = e; }
										if(f!=null && f.exists()) { f.delete(); }
										if(ex!=null) { logger.error("Error while close clob field and output file stream.",ex); }
									}
								}
							}catch(Error e){ 
								//에러무시.
								//java1.5에서 데이터가 실제 null인 경우이므로 무시한다.
							}
								
							//파싱할 수 없는 자료형 이거나 정말 NULL 값인 경우
							keyValueMap.put(columnName[i], "");
						}
					} else {
						keyValueMap.put(columnName[i], str.trim());
					}
				}
				
				dataSet[bulkCount] = keyValueMap;
				
//				if(useBackup){
//					try {
//						backupWriter.write("<doc>");
//						backupWriter.newLine();
//					} catch (IOException e) {
//						logger.error("Backup writer error => "+e.getMessage(),e);
//					}
//				}
				
//				for (int i = 0; i < fieldSettingList.size(); i++) {
//					FieldSetting fs = fieldSettingList.get(i);
//					Object value = null;
//					if(fs.isModify()){
//						if(sourceModifier != null){
//							try{
//								value = sourceModifier.modify(fs.getId(), keyValueMap);
//							}catch(IRException e){
//								logger.error(e.toString(),e);
//							}
//						}
//					}else{
//						//value = r.getString(fs.name);
//						value = keyValueMap.get(fs.getId());
//						if(value == null){
//							logger.error("DB에 {} 필드가 존재하지 않거나 해당 필드를 수집쿼리에서 SELECT하지 않았습니다.", fs.getId());
//							throw new IRException("DB에 "+fs.getId()+"필드가 존재하지 않거나 해당 필드를 수집쿼리에서 SELECT하지 않았습니다.");
//						}
//					}
					
//					if(fs.isBlob()){
//						//BLOB Field
//						fieldSet[bulkCount][i] = value;
//						if(useBackup){
//							try {
//								backupWriter.write("<");
//								backupWriter.write(fs.name);
//								backupWriter.write(">");
//								backupWriter.newLine();
//								backupWriter.write("(BLOB)");
//								backupWriter.newLine();
//								backupWriter.write("</");
//								backupWriter.write(fs.name);
//								backupWriter.write(">");
//								backupWriter.newLine();
//							} catch (IOException e) { }
//						}
//					}else{
						//문자필드
//						String str = null;
//						if(value != null)
//							str = (String) value;
//						
////						if(fs.normalize && str != null){
////							str = new String(scMap.getNormarlizedString(str));
////						}
//						
//						if(str == null) str = "";
//						
//						//html remove
//						if(fs.isRemoveTag()){
//							str = HTMLTagRemover.clean(str);
//						}
						
//						fieldSet[bulkCount][i] = str;
//						if(useBackup){
//							try {
//								backupWriter.write("<");
//								backupWriter.write(fs.getId());
//								backupWriter.write(">");
//								backupWriter.newLine();
//								backupWriter.write(str);
//								backupWriter.newLine();
//								backupWriter.write("</");
//								backupWriter.write(fs.getId());
//								backupWriter.write(">");
//								backupWriter.newLine();
//							} catch (IOException e) { }
//						}
						
//					}
					
//				}
				
//				if(hasLob){
//					for (int i = 0; i < keyValueMap.size(); i++) {
//						Object val = keyValueMap.get(i);
//						if(val instanceof InputStream){
//							try {
//								((InputStream)val).close();
//							} catch (IOException e) {
//								logger.error(e.getMessage(),e);
//							}
//						} else if(val instanceof File) {
//							File vfile = (File)val;
//							if(vfile.exists()) {
//								try {
//									vfile.delete();
//								} catch (SecurityException e) {
//									logger.error(e.getMessage(),e);
//								}
//							}
//						}
//					}
//				}
				
				
//				if(useBackup){
//					try {
//						backupWriter.write("</doc>");
//						backupWriter.newLine();
//						backupWriter.newLine();
//					} catch (IOException e) { }
//				}
				
				//추가한 문서가 삭제리스트에 존재하면 삭제된것이 아니므로 리스트에서 빼준다.
//				deleteIdList.remove(fieldSet[bulkCount][idFieldIndex]);
//				if(deleteIdList.remove(fieldSet[bulkCount][idFieldIndex])){
//					logger.debug("Removed id = "+fieldSet[bulkCount][idFieldIndex]);
//				}else{
//					logger.debug("Not Removed = "+fieldSet[bulkCount][idFieldIndex]);
//				}
				
				bulkCount++;
				totalCnt++;
				
				if(bulkCount >= BULK_SIZE) break;
			}
			
			
		}catch(SQLException e){
			
			try{
				if (r != null) r.close();
			} catch (SQLException e1) { }

			try{
				if (pstmt != null) pstmt.close();
			} catch (SQLException e1) { }

			try{
				if (con != null && !con.isClosed()) con.close();
			} catch (SQLException e1) { }
			try{
				if (backupWriter != null) backupWriter.close();
			} catch (IOException e1) { }
			
			throw new IRException(e);
		}
	}

}