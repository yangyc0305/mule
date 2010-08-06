/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSource, Inc.  All rights reserved.  http://www.mulesource.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.launcher;

import org.mule.module.reboot.MuleContainerBootstrapUtils;
import org.mule.util.FileUtils;
import org.mule.util.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DefaultMuleDeployer implements MuleDeployer
{

    protected transient final Log logger = LogFactory.getLog(getClass());

    public void deploy(Application app)
    {
        try
        {
            app.install();
            app.init();
            app.start();
        }
        catch (Throwable t)
        {
            // TODO logging
            t.printStackTrace();
        }
    }

    public void undeploy(Application app)
    {
        try
        {
            app.stop();
            app.dispose();
            final File appDir = new File(MuleContainerBootstrapUtils.getMuleAppsDir(), app.getAppName());
            FileUtils.deleteDirectory(appDir);
        }
        catch (Throwable t)
        {
            // TODO logging
            t.printStackTrace();
        }

    }

    public Application installFromAppDir(String packedMuleAppFileName) throws IOException
    {
        final File appsDir = MuleContainerBootstrapUtils.getMuleAppsDir();
        File appFile = new File(appsDir, packedMuleAppFileName);
        // basic security measure: outside apps dir use installFrom(url) and go through any
        // restrictions applied to it
        if (!appFile.getParentFile().equals(appsDir))
        {
            throw new SecurityException("installFromAppDir() can only deploy from $MULE_HOME/apps. Use installFrom(url) instead.");
        }
        return installFrom(appFile.toURL());
    }

    public Application installFrom(URL url) throws IOException
    {
        // TODO plug in app-bloodhound/validator here?
        if (!url.toString().endsWith(".zip"))
        {
            throw new IllegalArgumentException("Only Mule application zips are supported: " + url);
        }

        final File appsDir = MuleContainerBootstrapUtils.getMuleAppsDir();
        String appName;
        try
        {
            final String fullPath = url.toURI().toString();

            if (logger.isInfoEnabled())
            {
                logger.info("Exploding a Mule application archive: " + fullPath);
            }

            appName = FilenameUtils.getBaseName(fullPath);
            File appDir = new File(appsDir, appName);
            // normalize the full path + protocol to make unzip happy
            final File source = new File(url.toURI());
            
            FileUtils.unzip(source, appDir);
            if ("file".equals(url.getProtocol()))
            {
                FileUtils.deleteQuietly(source);
            }
        }
        catch (URISyntaxException e)
        {
            final IOException ex = new IOException(e.getMessage());
            ex.fillInStackTrace();
            throw ex;
        }

        // appname is never null by now
        return new ApplicationWrapper(new DefaultMuleApplication(appName));
    }
}
