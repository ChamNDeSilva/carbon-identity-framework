/*
 * Copyright (c) 2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.equinox.http.helper.ContextPathServletAdaptor;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.HttpService;
import org.wso2.carbon.identity.application.authentication.framework.ApplicationAuthenticationService;
import org.wso2.carbon.identity.application.authentication.framework.ApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.LocalApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.RequestPathApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.config.ConfigurationFacade;
import org.wso2.carbon.identity.application.authentication.framework.exception.FrameworkException;
import org.wso2.carbon.identity.application.authentication.framework.inbound.HttpIdentityRequestFactory;
import org.wso2.carbon.identity.application.authentication.framework.inbound.HttpIdentityResponseFactory;
import org.wso2.carbon.identity.application.authentication.framework.inbound.IdentityProcessor;
import org.wso2.carbon.identity.application.authentication.framework.inbound.IdentityServlet;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.claim.ClaimHandler;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.extension
        .AbstractPostHandler;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.extension
        .AbstractPreHandler;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.extension.ExtensionHandlerPoints;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.jit.JITHandler;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.response.AbstractResponseHandler;
import org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.authentication
        .AuthenticationHandler;
import org.wso2.carbon.identity.application.authentication.framework.listener.AuthenticationEndpointTenantActivityListener;
import org.wso2.carbon.identity.application.authentication.framework.servlet.CommonAuthenticationServlet;
import org.wso2.carbon.identity.application.authentication.framework.store.SessionDataStore;
import org.wso2.carbon.identity.application.common.ApplicationAuthenticatorService;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.LocalAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.application.common.model.RequestPathAuthenticatorConfig;
import org.wso2.carbon.identity.core.util.IdentityCoreInitializedEvent;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.carbon.user.core.service.RealmService;

import javax.servlet.Servlet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @scr.component name="identity.application.authentication.framework.component"
 * immediate="true"
 * @scr.reference name="osgi.httpservice"
 * interface="org.osgi.service.http.HttpService"
 * cardinality="1..1" policy="dynamic" bind="setHttpService"
 * unbind="unsetHttpService"
 * @scr.reference name="user.realmservice.default"
 * interface="org.wso2.carbon.user.core.service.RealmService"
 * cardinality="1..1" policy="dynamic" bind="setRealmService"
 * unbind="unsetRealmService"
 * @scr.reference name="registry.service"
 * interface="org.wso2.carbon.registry.core.service.RegistryService"
 * cardinality="1..1" policy="dynamic" bind="setRegistryService"
 * unbind="unsetRegistryService"
 * @scr.reference name="application.authenticator"
 * interface="org.wso2.carbon.identity.application.authentication.framework.ApplicationAuthenticator"
 * cardinality="1..n" policy="dynamic" bind="setAuthenticator"
 * unbind="unsetAuthenticator"
 * @scr.reference name="identityCoreInitializedEventService"
 * interface="org.wso2.carbon.identity.core.util.IdentityCoreInitializedEvent" cardinality="1..1"
 * policy="dynamic" bind="setIdentityCoreInitializedEventService" unbind="unsetIdentityCoreInitializedEventService"
 * @scr.reference name="identity.processor"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.IdentityProcessor"
 * cardinality="0..n" policy="dynamic" bind="addIdentityProcessor"
 * unbind="removeIdentityProcessor"
 * @scr.reference name="identity.request.factory"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.HttpIdentityRequestFactory"
 * cardinality="0..n" policy="dynamic" bind="addHttpIdentityRequestFactory"
 * unbind="removeHttpIdentityRequestFactory"
 * @scr.reference name="identity.response.factory"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.HttpIdentityResponseFactory"
 * cardinality="0..n" policy="dynamic" bind="addHttpIdentityResponseFactory"
 * unbind="removeHttpIdentityResponseFactory"
 * @scr.reference name="identity.handlers.authentication"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.authentication.AuthenticationHandler"
 * cardinality="0..n" policy="dynamic" bind="addAuthenticationHandler"
 * unbind="removeAuthenticationHandler"
 * @scr.reference name="identity.handlers.authorization"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.authorization.AbstractAuthorizationHandler"
 * cardinality="0..n" policy="dynamic" bind="addAuthorizationHandler"
 * unbind="removeAuthorizationHandler"
 * @scr.reference name="identity.handlers.jit"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.jit.JITHandler"
 * cardinality="0..n" policy="dynamic" bind="addJITHandler"
 * unbind="removeJITHandler"
 * @scr.reference name="identity.handlers.claim"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.claim.ClaimHandler"
 * cardinality="0..n" policy="dynamic" bind="addClaimHandler"
 * unbind="removeClaimHandler"
 * @scr.reference name="identity.handlers.response"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.response.AbstractResponseHandler"
 * cardinality="0..n" policy="dynamic" bind="addResponseHandler"
 * unbind="removeResponseHandler"
 * @scr.reference name="identity.handlers.extension"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.extension.AbstractPreHandler"
 * cardinality="0..n" policy="dynamic" bind="addPreHandler"
 * unbind="removePreHandler"
 * @scr.reference name="identity.handlers.extension"
 * interface="org.wso2.carbon.identity.application.authentication.framework.inbound.processor.handler.extension.AbstractPostHandler"
 * cardinality="0..n" policy="dynamic" bind="addPostHandler"
 * unbind="removePostHandler"
 */



public class FrameworkServiceComponent {

    public static final String COMMON_SERVLET_URL = "/commonauth";
    private static final String IDENTITY_SERVLET_URL = "/identity";
    private static final Log log = LogFactory.getLog(FrameworkServiceComponent.class);

    private HttpService httpService;

    public static RealmService getRealmService() {
        return FrameworkServiceDataHolder.getInstance().getRealmService();
    }

    protected void setRealmService(RealmService realmService) {
        if (log.isDebugEnabled()) {
            log.debug("RealmService is set in the Application Authentication Framework bundle");
        }
        FrameworkServiceDataHolder.getInstance().setRealmService(realmService);
    }

    public static RegistryService getRegistryService() {
        return FrameworkServiceDataHolder.getInstance().getRegistryService();
    }

    protected void setRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("RegistryService is set in the Application Authentication Framework bundle");
        }
        FrameworkServiceDataHolder.getInstance().setRegistryService(registryService);
    }

    public static BundleContext getBundleContext() throws FrameworkException {
        BundleContext bundleContext = FrameworkServiceDataHolder.getInstance().getBundleContext();
        if (bundleContext == null) {
            String msg = "System has not been started properly. Bundle Context is null.";
            log.error(msg);
            throw new FrameworkException(msg);
        }

        return bundleContext;
    }

    public static List<ApplicationAuthenticator> getAuthenticators() {
        return FrameworkServiceDataHolder.getInstance().getAuthenticators();
    }

    @SuppressWarnings("unchecked")
    protected void activate(ComponentContext ctxt) {
        BundleContext bundleContext = ctxt.getBundleContext();
        bundleContext.registerService(ApplicationAuthenticationService.class.getName(), new
                ApplicationAuthenticationService(), null);
        ;
        boolean tenantDropdownEnabled = ConfigurationFacade.getInstance().getTenantDropdownEnabled();

        if (tenantDropdownEnabled) {
            // Register the tenant management listener for tracking changes to tenants
            bundleContext.registerService(TenantMgtListener.class.getName(),
                                          new AuthenticationEndpointTenantActivityListener(), null);

            if (log.isDebugEnabled()) {
                log.debug("AuthenticationEndpointTenantActivityListener is registered. Tenant Domains Dropdown is " +
                          "enabled.");
            }
        }

        // Register Common servlet
        Servlet commonAuthServlet = new ContextPathServletAdaptor(new CommonAuthenticationServlet(),
                COMMON_SERVLET_URL);

        Servlet identityServlet = new ContextPathServletAdaptor(new IdentityServlet(),
                IDENTITY_SERVLET_URL);
        try {
            httpService.registerServlet(COMMON_SERVLET_URL, commonAuthServlet, null, null);
            httpService.registerServlet(IDENTITY_SERVLET_URL, identityServlet, null, null);
        } catch (Exception e) {
            String errMsg = "Error when registering servlets via the HttpService.";
            log.error(errMsg, e);
            throw new RuntimeException(errMsg, e);
        }

        //Default HttpIdentityRequestFactory is registered.
        HttpIdentityRequestFactory httpIdentityRequestFactory = new HttpIdentityRequestFactory();
        addHttpIdentityRequestFactory(httpIdentityRequestFactory);

        FrameworkServiceDataHolder.getInstance().setBundleContext(bundleContext);

        //this is done to load SessionDataStore class and start the cleanup tasks.
        SessionDataStore.getInstance();

        if (log.isDebugEnabled()) {
            log.debug("Application Authentication Framework bundle is activated");
        }
    }

    protected void deactivate(ComponentContext ctxt) {
        if (log.isDebugEnabled()) {
            log.debug("Application Authentication Framework bundle is deactivated");
        }

        FrameworkServiceDataHolder.getInstance().setBundleContext(null);
    }

    protected void setHttpService(HttpService httpService) {
        if (log.isDebugEnabled()) {
            log.debug("HTTP Service is set in the Application Authentication Framework bundle");
        }

        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        if (log.isDebugEnabled()) {
            log.debug("HTTP Service is unset in the Application Authentication Framework bundle");
        }

        this.httpService = null;
    }

    protected void unsetRealmService(RealmService realmService) {
        if (log.isDebugEnabled()) {
            log.debug("RealmService is unset in the Application Authentication Framework bundle");
        }
        FrameworkServiceDataHolder.getInstance().setRealmService(null);
    }

    protected void unsetRegistryService(RegistryService registryService) {
        if (log.isDebugEnabled()) {
            log.debug("RegistryService is unset in the Application Authentication Framework bundle");
        }
        FrameworkServiceDataHolder.getInstance().setRegistryService(null);
    }

    protected void setAuthenticator(ApplicationAuthenticator authenticator) {

        FrameworkServiceDataHolder.getInstance().getAuthenticators().add(authenticator);

        Property[] configProperties = null;

        if (authenticator.getConfigurationProperties() != null
            && !authenticator.getConfigurationProperties().isEmpty()) {
            configProperties = authenticator.getConfigurationProperties().toArray(new Property[0]);
        }

        if (authenticator instanceof LocalApplicationAuthenticator) {
            LocalAuthenticatorConfig localAuthenticatorConfig = new LocalAuthenticatorConfig();
            localAuthenticatorConfig.setName(authenticator.getName());
            localAuthenticatorConfig.setProperties(configProperties);
            localAuthenticatorConfig.setDisplayName(authenticator.getFriendlyName());
            ApplicationAuthenticatorService.getInstance().addLocalAuthenticator(localAuthenticatorConfig);
        } else if (authenticator instanceof FederatedApplicationAuthenticator) {
            FederatedAuthenticatorConfig federatedAuthenticatorConfig = new FederatedAuthenticatorConfig();
            federatedAuthenticatorConfig.setName(authenticator.getName());
            federatedAuthenticatorConfig.setProperties(configProperties);
            federatedAuthenticatorConfig.setDisplayName(authenticator.getFriendlyName());
            ApplicationAuthenticatorService.getInstance().addFederatedAuthenticator(federatedAuthenticatorConfig);
        } else if (authenticator instanceof RequestPathApplicationAuthenticator) {
            RequestPathAuthenticatorConfig reqPathAuthenticatorConfig = new RequestPathAuthenticatorConfig();
            reqPathAuthenticatorConfig.setName(authenticator.getName());
            reqPathAuthenticatorConfig.setProperties(configProperties);
            reqPathAuthenticatorConfig.setDisplayName(authenticator.getFriendlyName());
            ApplicationAuthenticatorService.getInstance().addRequestPathAuthenticator(reqPathAuthenticatorConfig);
        }

        if (log.isDebugEnabled()) {
            log.debug("Added application authenticator : " + authenticator.getName());
        }
    }

    protected void unsetAuthenticator(ApplicationAuthenticator authenticator) {

        FrameworkServiceDataHolder.getInstance().getAuthenticators().remove(authenticator);
        String authenticatorName = authenticator.getName();
        ApplicationAuthenticatorService appAuthenticatorService = ApplicationAuthenticatorService.getInstance();

        if (authenticator instanceof LocalApplicationAuthenticator) {
            LocalAuthenticatorConfig localAuthenticatorConfig = appAuthenticatorService.getLocalAuthenticatorByName
                    (authenticatorName);
            appAuthenticatorService.removeLocalAuthenticator(localAuthenticatorConfig);
        } else if (authenticator instanceof FederatedApplicationAuthenticator) {
            FederatedAuthenticatorConfig federatedAuthenticatorConfig = appAuthenticatorService
                    .getFederatedAuthenticatorByName(authenticatorName);
            appAuthenticatorService.removeFederatedAuthenticator(federatedAuthenticatorConfig);
        } else if (authenticator instanceof RequestPathApplicationAuthenticator) {
            RequestPathAuthenticatorConfig reqPathAuthenticatorConfig = appAuthenticatorService
                    .getRequestPathAuthenticatorByName(authenticatorName);
            appAuthenticatorService.removeRequestPathAuthenticator(reqPathAuthenticatorConfig);
        }

        if (log.isDebugEnabled()) {
            log.debug("Removed application authenticator : " + authenticator.getName());
        }

    }

    protected void addIdentityProcessor(IdentityProcessor requestProcessor) {

        FrameworkServiceDataHolder.getInstance().getIdentityProcessors().add(requestProcessor);
        Collections.sort(FrameworkServiceDataHolder.getInstance().getIdentityProcessors(),
                identityProcessor);

        if (log.isDebugEnabled()) {
            log.debug("Added IdentityProcessor : " + requestProcessor.getName());
        }
    }

    protected void removeIdentityProcessor(IdentityProcessor requestProcessor) {

        FrameworkServiceDataHolder.getInstance().getIdentityProcessors().remove(requestProcessor);

        if (log.isDebugEnabled()) {
            log.debug("Removed IdentityProcessor : " + requestProcessor.getName());
        }
    }

    protected void addHttpIdentityRequestFactory(HttpIdentityRequestFactory factory) {

        FrameworkServiceDataHolder.getInstance().getHttpIdentityRequestFactories().add(factory);
        Collections.sort(FrameworkServiceDataHolder.getInstance().getHttpIdentityRequestFactories(),
                httpIdentityRequestFactory);
        if (log.isDebugEnabled()) {
            log.debug("Added HttpIdentityRequestFactory : " + factory.getName());
        }
    }

    protected void removeHttpIdentityRequestFactory(HttpIdentityRequestFactory factory) {

        FrameworkServiceDataHolder.getInstance().getHttpIdentityRequestFactories().remove(factory);
        if (log.isDebugEnabled()) {
            log.debug("Removed HttpIdentityRequestFactory : " + factory.getName());
        }
    }

    protected void addHttpIdentityResponseFactory(HttpIdentityResponseFactory factory) {

        FrameworkServiceDataHolder.getInstance().getHttpIdentityResponseFactories().add(factory);
        Collections.sort(FrameworkServiceDataHolder.getInstance().getHttpIdentityResponseFactories(),
                httpIdentityResponseFactory);
        if (log.isDebugEnabled()) {
            log.debug("Added HttpIdentityResponseFactory : " + factory.getName());
        }
    }

    protected void removeHttpIdentityResponseFactory(HttpIdentityResponseFactory factory) {

        FrameworkServiceDataHolder.getInstance().getHttpIdentityResponseFactories().remove(factory);
        if (log.isDebugEnabled()) {
            log.debug("Removed HttpIdentityResponseFactory : " + factory.getName());
        }
    }



    protected void addAuthenticationHandler(AuthenticationHandler authenticationHandler) {

        FrameworkServiceDataHolder.getInstance().getAuthenticationHandlers().add(authenticationHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added AuthenticationHandler : " + authenticationHandler.getName());
        }
    }

    protected void removeAuthenticationHandler(AuthenticationHandler authenticationHandler) {

        FrameworkServiceDataHolder.getInstance().getAuthenticationHandlers().remove(authenticationHandler);

        if (log.isDebugEnabled()) {
            log.debug("Removed AuthenticationHandler : " + authenticationHandler.getName());
        }
    }

    protected void addJITHandler(JITHandler jitHandler) {

        FrameworkServiceDataHolder.getInstance().getJitHandlers().add(jitHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added JITHandler : " + jitHandler.getName());
        }
    }

    protected void removeJITHandler(JITHandler jitHandler) {

        FrameworkServiceDataHolder.getInstance().getJitHandlers().remove(jitHandler);

        if (log.isDebugEnabled()) {
            log.debug("Removed JITHandler : " + jitHandler.getName());
        }
    }

    protected void addClaimHandler(ClaimHandler claimHandler) {

        FrameworkServiceDataHolder.getInstance().getClaimHandlers().add(claimHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added ClaimHandler : " + claimHandler.getName());
        }
    }

    protected void removeClaimHandler(ClaimHandler claimHandler) {

        FrameworkServiceDataHolder.getInstance().getClaimHandlers().remove(claimHandler);

        if (log.isDebugEnabled()) {
            log.debug("Removed ClaimHandler : " + claimHandler.getName());
        }
    }

    protected void addResponseHandler(AbstractResponseHandler responseHandler) {

        FrameworkServiceDataHolder.getInstance().getResponseHandlers().add(responseHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added AbstractResponseHandler : " + responseHandler.getName());
        }
    }

    protected void removeResponseHandler(AbstractResponseHandler responseHandler) {

        FrameworkServiceDataHolder.getInstance().getResponseHandlers().remove(responseHandler);

        if (log.isDebugEnabled()) {
            log.debug("Removed AbstractResponseHandler : " + responseHandler.getName());
        }
    }


    protected void addPreHandler(AbstractPreHandler preHandler) {

        Map<ExtensionHandlerPoints, List<AbstractPreHandler>> preHandlerMap = FrameworkServiceDataHolder.getInstance().getPreHandler();

        List<AbstractPreHandler> abstractPreHandlers = preHandlerMap.get(preHandler.getExtensionHandlerPoints());
        if(abstractPreHandlers == null){
            abstractPreHandlers = new ArrayList<>();
            preHandlerMap.put(preHandler.getExtensionHandlerPoints(),abstractPreHandlers);
        }
        abstractPreHandlers.add(preHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added AbstractPreHandler : " + preHandler.getName());
        }
    }

    protected void removePreHandler(AbstractPreHandler preHandler) {

        Map<ExtensionHandlerPoints, List<AbstractPreHandler>> preHandlerMap = FrameworkServiceDataHolder.getInstance().getPreHandler();

        List<AbstractPreHandler> abstractPreHandlers = preHandlerMap.get(preHandler.getExtensionHandlerPoints());
        if(abstractPreHandlers != null) {
            abstractPreHandlers.remove(preHandler);
        }
        if (log.isDebugEnabled()) {
            log.debug("Removed AbstractPreHandler : " + preHandler.getName());
        }
    }



    protected void addPostHandler(AbstractPostHandler postHandler) {

        Map<ExtensionHandlerPoints, List<AbstractPostHandler>> postHandlerMap  = FrameworkServiceDataHolder.getInstance().getPostHandler();

        List<AbstractPostHandler> abstractPostHandlers = postHandlerMap.get(postHandler.getExtensionHandlerPoints());
        if(abstractPostHandlers == null){
            abstractPostHandlers = new ArrayList<>();
            postHandlerMap.put(postHandler.getExtensionHandlerPoints(),abstractPostHandlers);
        }
        abstractPostHandlers.add(postHandler);

        if (log.isDebugEnabled()) {
            log.debug("Added AbstractPostHandler : " + postHandler.getName());
        }
    }

    protected void removePostHandler(AbstractPostHandler postHandler) {

        Map<ExtensionHandlerPoints, List<AbstractPostHandler>> postHandlerMap  = FrameworkServiceDataHolder.getInstance().getPostHandler();

        List<AbstractPostHandler> abstractPostHandlers = postHandlerMap.get(postHandler.getExtensionHandlerPoints());
        if(abstractPostHandlers != null) {
            abstractPostHandlers.remove(postHandler);
        }
        if (log.isDebugEnabled()) {
            log.debug("Removed AbstractPostHandler : " + postHandler.getName());
        }
    }




    protected void unsetIdentityCoreInitializedEventService(IdentityCoreInitializedEvent identityCoreInitializedEvent) {
        /* reference IdentityCoreInitializedEvent service to guarantee that this component will wait until identity core
         is started */
    }

    protected void setIdentityCoreInitializedEventService(IdentityCoreInitializedEvent identityCoreInitializedEvent) {
        /* reference IdentityCoreInitializedEvent service to guarantee that this component will wait until identity core
         is started */
    }

    private static Comparator<IdentityProcessor> identityProcessor =
            new Comparator<IdentityProcessor>() {

                @Override
                public int compare(IdentityProcessor identityProcessor1,
                                   IdentityProcessor identityProcessor2) {

                    if (identityProcessor1.getPriority() > identityProcessor2.getPriority()) {
                        return 1;
                    } else if (identityProcessor1.getPriority() < identityProcessor2.getPriority()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            };

    private static Comparator<HttpIdentityRequestFactory> httpIdentityRequestFactory =
            new Comparator<HttpIdentityRequestFactory>() {

                @Override
                public int compare(HttpIdentityRequestFactory factory1,
                                   HttpIdentityRequestFactory factory2) {

                    if (factory1.getPriority() > factory2.getPriority()) {
                        return 1;
                    } else if (factory1.getPriority() < factory2.getPriority()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            };

    private static Comparator<HttpIdentityResponseFactory> httpIdentityResponseFactory =
            new Comparator<HttpIdentityResponseFactory>() {

                @Override
                public int compare(HttpIdentityResponseFactory factory1,
                                   HttpIdentityResponseFactory factory2) {

                    if (factory1.getPriority() > factory2.getPriority()) {
                        return 1;
                    } else if (factory1.getPriority() < factory2.getPriority()) {
                        return -1;
                    } else {
                        return 0;
                    }
                }
            };
}
