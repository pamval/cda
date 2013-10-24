/*!
* Copyright 2002 - 2013 Webdetails, a Pentaho company.  All rights reserved.
* 
* This software was developed by Webdetails and is provided under the terms
* of the Mozilla Public License, Version 2.0, or any later version. You may not use
* this file except in compliance with the license. If you need a copy of the license,
* please go to  http://mozilla.org/MPL/2.0/. The Initial Developer is Webdetails.
*
* Software distributed under the Mozilla Public License is distributed on an "AS IS"
* basis, WITHOUT WARRANTY OF ANY KIND, either express or  implied. Please refer to
* the license for the specific language governing your rights and limitations.
*/

package pt.webdetails.cda;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.pentaho.platform.api.engine.IParameterProvider;

import pt.webdetails.cda.cache.CacheScheduleManager;
import pt.webdetails.cda.cache.monitor.CacheMonitorHandler;
import pt.webdetails.cda.exporter.ExportOptions;
import pt.webdetails.cda.exporter.ExportedQueryResult;
import pt.webdetails.cda.services.CacheManager;
import pt.webdetails.cda.services.Editor;
import pt.webdetails.cda.services.Previewer;
import pt.webdetails.cda.utils.DoQueryParameters;
import pt.webdetails.cpf.SimpleContentGenerator;
import pt.webdetails.cpf.annotations.AccessLevel;
import pt.webdetails.cpf.annotations.Audited;
import pt.webdetails.cpf.annotations.Exposed;
import pt.webdetails.cpf.repository.api.IReadAccess;


public class CdaContentGenerator extends SimpleContentGenerator
{
  public static final String PLUGIN_NAME = "cda";
  private static final long serialVersionUID = 1L;
  private static final String PREFIX_PARAMETER = "param";
  private static final String PREFIX_SETTING = "setting";

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  @Audited(action = "doQuery")
  public void doQuery(final OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    DoQueryParameters parameters = getDoQueryParameters( requestParams );
    CdaCoreService service = getCoreService();
    if ( parameters.isWrapItUp() ) {
      writeOut( out, service.wrapQuery( parameters ) );
    }
    else {
      service.doQuery( parameters ).writeResponse( getResponse() );
    }
  }



  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void unwrapQuery(final OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    String cda = getPath( requestParams );
    CdaCoreService coreService = getCoreService();
    coreService.unwrapQuery( cda, requestParams.getStringParameter("uuid", null) ).writeResponse( getResponse() );

  }

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void listQueries(final OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    CdaCoreService service = getCoreService();
    String cda = getPath( requestParams );
    String outputType = requestParams.getStringParameter("outputType", "json");
    service.listQueries( cda, getSimpleExportOptions( outputType ) ).writeResponse( getResponse() );
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void listParameters(final OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    DoQueryParameters params = getDoQueryParameters( requestParams );
    
    CdaCoreService service = getCoreService();
    ExportedQueryResult result = service.listParameters(
            params.getPath(),
            params.getDataAccessId(),
            getSimpleExportOptions( params.getOutputType() ) );
    result.writeResponse( getResponse() );

  }


  @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.XML)
  public void getCdaFile(final OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    // XXX return Json
    String repoPath = requestParams.getStringParameter("path", null);
    String result = getEditor().getFile( repoPath );
    writeOut( out, result );
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.JSON)
  public void writeCdaFile(OutputStream out) throws Exception
  {
    final IParameterProvider requestParams = getRequestParameters();
    String path = getPath( requestParams );
    String data = requestParams.getStringParameter("data", null);
    if ( data == null ) {
      throw new IllegalArgumentException( "data" );
    }
    if ( StringUtils.isEmpty( path ) ) {
      throw new IllegalArgumentException( "path" );
    }
    boolean result = getEditor().writeFile( path, data );
    writeOut ( out, "" + result );
    //FIXME write success/failure json
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void getCdaList(final OutputStream out) throws Exception
  {
    CdaCoreService service = getCoreService();
    final String outputType = getOutputType( getRequestParameters() );
    service.getCdaList( getSimpleExportOptions( outputType ) );
  }

  @Exposed(accessLevel = AccessLevel.ADMIN, outputType = MimeType.PLAIN_TEXT)
  public void clearCache(final OutputStream out) throws Exception
  {
    CdaCoreService service = getCoreService();
    service.clearCache();
  }
  
  @Exposed(accessLevel = AccessLevel.ADMIN)
  public void cacheMonitor(final OutputStream out) throws Exception{
    //XXX
    IParameterProvider parameters = getRequestParameters();
    String method = parameters.getStringParameter("method", null);
    CacheMonitorHandler.getInstance().handleCall(method, parameters, out);
  }

  @Override
  public Log getLogger()
  {
    return logger;
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC,  outputType = MimeType.HTML)
  public void editFile(final OutputStream out) throws Exception
  {
    String path = getPath(getRequestParameters());
    InputStream editorSrc = null;
    try {
      editorSrc = getEditor().getEditor(path);
      IOUtils.copy( editorSrc, out );
    } 
    finally {
      IOUtils.closeQuietly( editorSrc );
    }
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void previewQuery(final OutputStream out) throws Exception
  {
    Previewer previewer = new Previewer();
    writeOut(out, previewer.previewQuery());
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.CSS)
  public void getCssResource(final OutputStream out) throws Exception
  {
    
    getSystemResource(out, getRequestParameters().getStringParameter("resource", null));
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.JAVASCRIPT)
  public void getJsResource(final OutputStream out) throws Exception
  {
    getSystemResource(out, getRequestParameters().getStringParameter("resource", null));
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.JSON)
  public void listDataAccessTypes(final OutputStream out) throws Exception
  {
    boolean refreshCache = Boolean.parseBoolean(getRequestParameters().getStringParameter("refreshCache", "false"));

    CdaCoreService coreService = getCoreService();
    writeOut( out, coreService.listDataAccessTypes(refreshCache) );
  }

  @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void cacheController(OutputStream out)
  {
      String method = getRequestParameters().getParameter("method").toString();
      String obj = getRequestParameters().getStringParameter("object", "");
      
      CacheScheduleManager.getInstance().handleCall(method,obj, out);
  }

  @Exposed(accessLevel = AccessLevel.ADMIN)
  public void manageCache(final OutputStream out) throws Exception
  {
    CacheManager cacheMan = new CacheManager();
    writeOut(out, cacheMan.manageCache());
  }

  @Override
  public String getPluginName() {
    return "cda";
  }

  private DoQueryParameters getDoQueryParameters( final IParameterProvider requestParams ) {
    DoQueryParameters parameters = getParamsWithPath( requestParams );

    parameters.setBypassCache(Boolean.parseBoolean(requestParams.getStringParameter("bypassCache","false")));
    parameters.setDataAccessId(requestParams.getStringParameter("dataAccessId", "<blank>"));
    parameters.setOutputIndexId(Integer.parseInt(requestParams.getStringParameter("outputIndexId", "1")));
    parameters.setOutputType(requestParams.getStringParameter("outputType", "json"));
    parameters.setPageSize((int)requestParams.getLongParameter("pageSize", 0));
    parameters.setPageStart((int)requestParams.getLongParameter("pageStart", 0));
    parameters.setPaginateQuery(Boolean.parseBoolean(requestParams.getStringParameter("paginateQuery", "false")));

    Map<String, Object> extraParams = new HashMap<String, Object>();
    Map<String, Object> extraSettings = new HashMap<String, Object>();
    @SuppressWarnings("unchecked")
    final Iterator<String> params = (Iterator<String>) requestParams.getParameterNames();
    while (params.hasNext())
    {
      final String param = params.next();

      if (param.startsWith(PREFIX_PARAMETER))
      {
        extraParams.put(param.substring(PREFIX_PARAMETER.length()), requestParams.getParameter(param));
      }
      else if (param.startsWith(PREFIX_SETTING))
      {
        extraSettings.put(param.substring(PREFIX_SETTING.length()), requestParams.getStringParameter(param, ""));
      }
    }
    
    parameters.setExtraParams(extraParams);
    
    parameters.setExtraSettings(extraSettings);
    
    final ArrayList<String> sortBy = new ArrayList<String>();
    String[] def =
    {
    };
    for (Object obj : requestParams.getArrayParameter("sortBy", def))
    {
      if (!((String) obj).equals(""))
      {
        sortBy.add((String) obj);
      }
    }
    parameters.setSortBy(sortBy);
    parameters.setWrapItUp(requestParams.getStringParameter("wrapItUp", null) != null);
    return parameters;
  }

  private Editor getEditor() {
    return new Editor();
  }

  private DoQueryParameters getParamsWithPath(IParameterProvider requestParams) {
    return new DoQueryParameters(
        (String)requestParams.getParameter("path"), 
        (String)requestParams.getParameter("solution"), 
        (String)requestParams.getParameter("file"));
  }

  private String getPath( IParameterProvider requestParams ) {
    DoQueryParameters parameters = getParamsWithPath(requestParams);
    return parameters.getPath();
  }

  private CdaCoreService getCoreService() {
    return new CdaCoreService();
  }

  private String getOutputType ( IParameterProvider requestParams ) {
    return requestParams.getStringParameter( "outputType", "json" );
  }
  private ExportOptions getSimpleExportOptions( final String outputType ) {
    return new ExportOptions() {
      
      public String getOutputType() {
        return outputType;
      }
      
      public Map<String, String> getExtraSettings() {
        return Collections.<String,String>emptyMap();
      }
    };
  }


  private void getSystemResource(final OutputStream out, String resource) throws Exception
  {
    IReadAccess repo = getRepositoryAccess();
    if ( repo.fileExists( resource ) ) {
      InputStream in = null;
      try {
        in = repo.getFileInputStream( resource );
        IOUtils.copy( in, out );
        out.flush();
      }
      finally {
        IOUtils.closeQuietly( in );
      }
      
    }
  }

  private IReadAccess getRepositoryAccess() {
    return CdaEngine.getRepo().getPluginSystemReader( null );
  }
}
