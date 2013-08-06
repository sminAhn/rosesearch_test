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

package org.fastcatsearch.servlet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fastcatsearch.control.JobService;
import org.fastcatsearch.control.ResultFuture;
import org.fastcatsearch.ir.group.GroupResult;
import org.fastcatsearch.ir.group.GroupResults;
import org.fastcatsearch.ir.query.Result;
import org.fastcatsearch.ir.query.Row;
import org.fastcatsearch.ir.util.Formatter;
import org.fastcatsearch.job.DocumentListJob;

public class DocumentListServletBak extends WebServiceHttpServlet {
	
    public DocumentListServletBak(int resultType){
    	super(resultType);
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	Enumeration enumeration = request.getParameterNames();
    	String timeoutStr = request.getParameter("timeout");
    	String isAdmin = request.getParameter("admin");
    	String collectionName = request.getParameter("cn");
    	String requestCharset = request.getParameter("requestCharset");
    	String responseCharset = request.getParameter("responseCharset");
    	StringBuffer sb = new StringBuffer();
    	while(enumeration.hasMoreElements()){
    		String key = (String) enumeration.nextElement();
    		String value = request.getParameter(key);
    		sb.append(key);
    		sb.append("=");
    		sb.append(value);
    		sb.append("&");
    	}
    	doReal(sb.toString(), timeoutStr, isAdmin, collectionName, response, requestCharset, responseCharset);
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	String queryString = request.getQueryString();
    	String timeoutStr = request.getParameter("timeout");
    	String isAdmin = request.getParameter("admin");
    	String collectionName = request.getParameter("cn");
    	String requestCharset = request.getParameter("requestCharset");
    	String responseCharset = request.getParameter("responseCharset");
    	doReal(queryString, timeoutStr, isAdmin, collectionName, response, requestCharset, responseCharset);
    	
    }
    
    private void doReal(String queryString, String timeoutStr, String isAdmin, String collectionName, HttpServletResponse response, String requestCharset, String responseCharset) throws ServletException, IOException {
    	if(requestCharset == null)
    		requestCharset = "UTF-8";
    	
    	if(responseCharset == null)
    		responseCharset = "UTF-8";
    	
    	logger.debug("requestCharset = "+requestCharset);
    	logger.debug("responseCharset = "+responseCharset);
    	queryString = URLDecoder.decode(queryString, requestCharset);
    	logger.debug("queryString = "+queryString);
    	
    	//TODO 디폴트 시간을 셋팅으로 빼자.
    	int timeout = 5;
    	if(timeoutStr != null)
    		timeout = Integer.parseInt(timeoutStr);
    	logger.debug("timeout = "+timeout+" s");
    	
		response.setCharacterEncoding(responseCharset);
    	response.setStatus(HttpServletResponse.SC_OK);
    	
    	if(resultType == JSON_TYPE){
    		response.setContentType("application/json; charset="+responseCharset);
    	}else if(resultType == XML_TYPE){
    		response.setContentType("text/xml; charset="+responseCharset);
    	}
    	
    	PrintWriter w = response.getWriter();
    	BufferedWriter writer = new BufferedWriter(w);
    	
    	
    	
    	long searchTime = 0;
    	long st = System.currentTimeMillis();
    	
    	DocumentListJob job = new DocumentListJob();
    	job.setArgs(new String[]{queryString});
    	
    	Result result = null;
    	
		ResultFuture jobResult = JobService.getInstance().offer(job);
		Object obj = jobResult.poll(timeout);
		searchTime = (System.currentTimeMillis() - st);
		if(jobResult.isSuccess()){
			result = (Result)obj;
		}else{
			String errorMsg = (String)obj;
			
			if(resultType == JSON_TYPE){
				if(errorMsg != null){
					errorMsg = Formatter.escapeJSon(errorMsg);
				}
				writer.write("{");
				writer.newLine();
	    		writer.write("\t\"status\": \"1\",");
	    		writer.newLine();
	    		writer.write("\t\"time\": \""+Formatter.getFormatTime(searchTime)+"\",");
	    		writer.newLine();
	    		writer.write("\t\"error_msg\": \""+errorMsg+"\"");
	    		writer.newLine();
	    		writer.write("}");
			}else if(resultType == XML_TYPE){
				if(errorMsg != null){
					errorMsg = Formatter.escapeXml(errorMsg);
				}
				writer.write("<fastcat>");
				writer.newLine();
	    		writer.write("\t<status>1</status>");
	    		writer.newLine();
	    		writer.write("\t<time>"+Formatter.getFormatTime(searchTime)+"</time>");
	    		writer.newLine();
	    		writer.write("\t<error_msg>"+errorMsg+"</error_msg>");
	    		writer.newLine();
	    		writer.write("</fastcat>");
			}
			
			writer.close();
    		return;
		}
		
    	//SUCCESS
		String logStr = searchTime+", "+result.getCount()+", "+result.getTotalCount()+", "+result.getFieldCount();
		if(result.getGroupResult() != null){
			String grStr = ", [";
			GroupResults aggregationResult = result.getGroupResult();//GroupResult[]
			GroupResult[] gr = aggregationResult.groupResultList();
			for (int i = 0; i < gr.length; i++) {
				if(i > 0)
					grStr += ", ";
				grStr += gr[i].size();
			}
			grStr += "]";
			logStr += grStr;
		}
		
		if(resultType == JSON_TYPE){
			//JSON
			int fieldCount = result.getFieldCount();
			writer.write("{");
			writer.newLine();
			writer.write("\t\"status\": \"0\",");
			writer.newLine();
			writer.write("\t\"time\": \""+Formatter.getFormatTime(searchTime)+"\",");
			writer.newLine();
			writer.write("\t\"total_count\": \""+result.getTotalCount()+"\",");
			writer.newLine();
			writer.write("\t\"count\": \""+result.getCount()+"\",");
			writer.newLine();
			writer.write("\t\"field_count\": \""+fieldCount+"\",");
			writer.newLine();
			
			writer.write("\t\"seg_count\": \""+result.getSegmentCount()+"\",");
			writer.newLine();
			writer.write("\t\"doc_count\": \""+result.getDocCount()+"\",");
			writer.newLine();
			writer.write("\t\"del_count\": \""+result.getDeletedDocCount()+"\",");
			writer.newLine();
			
			writer.write("\t\"fieldname_list\": [");
			writer.newLine();
			writer.write("\t\t");
			
			String[] fieldNames = result.getFieldNameList();
			
			if(result.getCount() == 0){
				writer.write("{\"name\": \"_no_\"}");
			}else{
	    		writer.write("{\"name\": \"_no_\"}, ");
	    		for (int i = 0; i < fieldNames.length; i++) {
	    			writer.write("{\"name\": ");
	    			writer.write("\""+fieldNames[i]+"\"}");
	    			if(i < fieldNames.length - 1)
						writer.write(",");
				}
	    		writer.write("");
			}
	    	
			writer.write("\t],");
			
			writer.write("\t\"result\":");
			writer.write("\t["); //array
			//data
			Row[] rows = result.getData();
			int start = result.getStart();
			
			if(rows.length == 0){
				writer.write("\t\t{\"_no_\": \"No result found!\"}");
				writer.write("\t]");
			}else{
	    		for (int i = 0; i < rows.length; i++) {
	    			writer.write("\t\t{");
					Row row = rows[i];
					writer.write("\t\t\"_no_\": \""+row.getRowTag()+"\",");
					for(int k = 0; k < fieldCount; k++) {
						char[] f = row.get(k);
						String fdata = new String(f).trim();
						fdata = Formatter.escapeJSon(fdata);
						writer.write("\t\t\""+fieldNames[k]+"\": \""+fdata+"\"");
	//						writer.write("\t\t\""+fieldNames[k]+"\": \""+URLEncoder.encode(fdata, "utf-8")+"\"");
						if(k < fieldCount - 1)
							writer.write(",");
						else
							writer.write("");
					}
					writer.write(",");
					//for delete set
					if (row.isDeleted()) {
						writer.write("\t\t\"_delete_\": \"true\"");
					}else{
						writer.write("\t\t\"_delete_\": \"false\"");
					}
					
					writer.write("\t\t}");
					if(i < rows.length - 1)
						writer.write(",");
					else
						writer.write("");
	    		}
	    		
	    		writer.write("\t]");
			}//if else
			writer.write("}");
		}else if(resultType == XML_TYPE){
			//XML
			//this does not support admin test, have no column meta data
			
			int fieldCount = result.getFieldCount();
			writer.write("<fastcat>");
			writer.newLine();
			writer.write("\t<status>0</status>");
			writer.newLine();
			writer.write("\t<time>");
			writer.write(Formatter.getFormatTime(searchTime));
			writer.write("</time>");
			writer.newLine();
			writer.write("\t<total_count>");
			writer.write(result.getTotalCount()+"");
			writer.write("</total_count>");
			writer.newLine();
			writer.write("\t<count>");
			writer.write(result.getCount()+"");
			writer.write("</count>");
			writer.newLine();
			writer.write("\t<field_count>");
			writer.write(fieldCount+"");
			writer.write("</field_count>");
			writer.newLine();
			
			String[] fieldNames = result.getFieldNameList();
			
			if(result.getCount() == 0){
				writer.write("\t<fieldname_list>");
				writer.newLine();
				writer.write("\t\t<name>_no_</name>");
				writer.newLine();
				writer.write("\t</fieldname_list>");
				writer.newLine();
			}else{
				writer.write("\t<fieldname_list>");
				writer.newLine();
	    		writer.write("\t\t<name>_no_</name>");
	    		writer.newLine();
				writer.write("\t</fieldname_list>");
				writer.newLine();
	    		for (int i = 0; i < fieldNames.length; i++) {
	    			writer.write("\t<fieldname_list>");
					writer.newLine();
	    			writer.write("\t\t<name>");
	    			writer.write(fieldNames[i]);
	    			writer.write("</name>");
	    			writer.newLine();
	    			writer.write("\t</fieldname_list>");
					writer.newLine();
				}
			}
	    	
			
			//data
			Row[] rows = result.getData();
			int start = result.getStart();
			
			if(rows.length == 0){
				writer.write("\t<result>");
				writer.newLine();
				writer.write("\t\t<_no_>No result found!</_no_>");
				writer.newLine();
				writer.write("\t</result>");
				writer.newLine();
			}else{
	    		for (int i = 0; i < rows.length; i++) {
					Row row = rows[i];
					writer.write("\t<result>");
					writer.newLine();
					writer.write("\t\t<_no_>");
					writer.write((start + i)+"");
					writer.write("</_no_>");
					writer.newLine();
					for(int k = 0; k < fieldCount; k++) {
						char[] f = row.get(k);
						String fdata = new String(f);
						fdata = Formatter.escapeXml(fdata);
						writer.write("\t\t<");
						writer.write(fieldNames[k]);
						writer.write(">");
						writer.write(fdata);
						writer.write("</");
						writer.write(fieldNames[k]);
						writer.write(">");
						writer.newLine();
					}
					writer.write("\t</result>");
					writer.newLine();
	    		}
			}//if else
			writer.write("</fastcat>");
		}
    	

    	writer.close();
    }
	
}
