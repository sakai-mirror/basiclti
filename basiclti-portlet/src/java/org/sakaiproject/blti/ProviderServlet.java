/**
 * $URL$
 * $Id$
 *
 * Copyright (c) 2009 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sakaiproject.blti;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthValidator;
import net.oauth.SimpleOAuthValidator;
import net.oauth.server.OAuthServlet;
import net.oauth.signature.OAuthSignatureMethod;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.imsglobal.basiclti.BasicLTIUtil;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ServerConfigurationService;
import org.sakaiproject.event.cover.UsageSessionService;
import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.Tool;
import org.sakaiproject.tool.cover.SessionManager;
import org.sakaiproject.tool.cover.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.util.ResourceLoader;

/**
 * Notes:
 * 
 * This program is directly exposed as a URL to receive IMS Basic LTI launches
 * so it must be carefully reviewed and any changes must be looked at carefully.
 * Here are some issues:
 * 
 * - This uses the RemoteHostFilter so by default it only accepts local IP
 * addresses. This configuration can be changed in web.xml or using the
 * webservices.allow, etc (see RemoteHostFilter)
 * 
 * - This will only function when it is enabled via sakai.properties
 * 
 * - This servlet makes use of security advisors - once an advisor has been
 * added, it must be removed - often in a finally. Also the code below only adds
 * the advisor for very short segments of code to allow for easier review.
 * 
 * Implemented using a SHA-1 hash of the effective context_id and then stores
 * the original context_id in a site.property "lti_context_id" which will be
 * useful for later reference. Since SHA-1 hashes to 40 chars, that would leave
 * us 59 chars (i.e. 58 + ":") to use for LTI key. This also means that the new
 * maximum supported size of an effective context_id is the maximum message size
 * of SHA-1: maximum length of (264 ? 1) bits.
 */

public class ProviderServlet extends HttpServlet {

	private static Log M_log = LogFactory.getLog(ProviderServlet.class);
	private static ResourceLoader rb = new ResourceLoader("basiclti");
	private static final String BASICLTI_RESOURCE_LINK = "blti:resource_link_id";
	/**
	 * Setup a security advisor.
	 */
	public void pushAdvisor() {
		// setup a security advisor
		SecurityService.pushAdvisor(new SecurityAdvisor() {
			public SecurityAdvice isAllowed(String userId, String function,
					String reference) {
				return SecurityAdvice.ALLOWED;
			}
		});
	}

	/**
	 * Remove our security advisor.
	 */
	public void popAdvisor() {
		SecurityService.popAdvisor();
	}

	public void doError(HttpServletRequest request,HttpServletResponse response, String s, String message, Exception e) throws java.io.IOException {
		if (e != null) {
			M_log.error(e.getLocalizedMessage(), e);
		}
		M_log.info(rb.getString(s) + ": " + message);
		String return_url = request.getParameter("launch_presentation_return_url");
		if (return_url != null && return_url.length() > 1) {
			if (return_url.indexOf('?') > 1) {
				return_url += "&lti_msg=" + URLEncoder.encode(rb.getString(s), "UTF-8");
			} else {
				return_url += "?lti_msg=" + URLEncoder.encode(rb.getString(s), "UTF-8");
			}
			response.sendRedirect(return_url);
			return;
		}
		PrintWriter out = response.getWriter();
		out.println(rb.getString(s));
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String ipAddress = request.getRemoteAddr();

		M_log.debug("Basic LTI Provider request from IP=" + ipAddress);

		String enabled = ServerConfigurationService.getString(
				"imsblti.provider.enabled", null);
		if (enabled == null || !("true".equals(enabled))) {
			M_log.warn("Basic LTI Provider is Disabled IP=" + ipAddress);
			response.setStatus(response.SC_FORBIDDEN);
			return;
		}

		boolean saved = false;
		
		Map<String,String[]> params = (Map<String,String[]>)request.getParameterMap();
		for (Map.Entry<String,String[]> param : params.entrySet()) {
			M_log.debug(param.getKey() + ":" + param.getValue()[0]);
		}

		String oauth_consumer_key = request.getParameter("oauth_consumer_key");
		String user_id = request.getParameter("user_id");
		String context_id = request.getParameter("context_id");
		String fname = request.getParameter("lis_person_name_given");
		String lname = request.getParameter("lis_person_name_family");
		String email = request.getParameter("lis_person_contact_email_primary");
		String resource_link_id = request.getParameter("resource_link_id");
		String lti_message_type = request.getParameter("lti_message_type");
		String lti_version = request.getParameter("lti_version");

		//check parameters
		if(!BasicLTIUtil.equals(lti_message_type, "basic-lti-launch-request")) {
			doError(request, response, "launch.invalid", "lti_message_type="+lti_message_type, null);
			return;
		}
		
		if(!BasicLTIUtil.equals(lti_version, "LTI-1p0")) {
			doError(request, response, "launch.invalid", "lti_version="+lti_version, null);
			return;
		}
		
		if(BasicLTIUtil.isBlank(oauth_consumer_key)) {
			doError(request, response, "launch.missing", "oauth_consumer_key", null);
			return;
		}
		
		if(BasicLTIUtil.isBlank(resource_link_id)) {
			doError(request, response, "launch.missing", "resource_link_id", null);
			return;
		}
		
		if(BasicLTIUtil.isBlank(user_id)) {
			doError(request, response, "launch.missing", "user_id", null);
			return;
		}
		M_log.debug("user_id="+user_id);

		//check tool_id
		String tool_id = request.getPathInfo();
		if (tool_id == null) {
			doError(request, response, "launch.tool_id.required", null, null);
			return;
		}

		// Trim off the leading slash and any trailing space
		tool_id = tool_id.substring(1).trim();
		M_log.debug("tool_id="+tool_id);
		String allowedToolsConfig = ServerConfigurationService.getString("imsblti.provider.allowedtools", null);
		
		String[] allowedTools = allowedToolsConfig.split(":");
		List<String> allowedToolsList = Arrays.asList(allowedTools);

		if (allowedTools != null && !allowedToolsList.contains(tool_id)) {
			doError(request, response, "launch.tool.notallowed", tool_id, null);
			return;
		}
		Tool toolCheck = ToolManager.getTool(tool_id);
		if (toolCheck == null) {
			doError(request, response, "launch.tool.notfound", tool_id, null);
			return;
		}

		// Get the list of highly trusted consumers from sakai.properties.
		// If the incoming consumer is highly trusted, we use the context_id and site_id as is, 
		// ie without prefixing them with the oauth_consumer_key first.
		// We also don't both checking their roles in the site.
		boolean isTrustedConsumer = false;
		String trustedConsumersConfig = ServerConfigurationService.getString("imsblti.highly.trusted.consumers", null);
		if(BasicLTIUtil.isNotBlank(trustedConsumersConfig)) {
			String[] trustedConsumers = trustedConsumersConfig.split(":");
			List<String> trustedConsumersList = Arrays.asList(trustedConsumers);

			if (trustedConsumersList.contains(oauth_consumer_key)) {
				isTrustedConsumer = true;
			}
		}
		
		M_log.debug("Consumer=" + oauth_consumer_key);
		M_log.debug("Trusted=" + isTrustedConsumer);
		
		
		// Contextualize the context_id with the OAuth consumer key
		// Also use the resource_link_id for the context_id if we did not get a context_id
		// BLTI-31: if trusted, content_id is required and use the param without modification
		if(BasicLTIUtil.isBlank(context_id)) {
			if(isTrustedConsumer) {
				doError(request, response, "launch.missing",context_id, null);
				return;
			} else {
				context_id = "res:" + resource_link_id;
			}
		}
		
		String siteId = null;
		if(isTrustedConsumer) {
			siteId = context_id;
		} else {	
			siteId = ShaUtil.sha1Hash(oauth_consumer_key + ":" + context_id);
		}
		M_log.debug("siteId="+siteId);

		// Lookup the secret
		String configPrefix = "imsblti.provider." + oauth_consumer_key + ".";
		String oauth_secret = ServerConfigurationService.getString(configPrefix+ "secret", null);
		if (oauth_secret == null) {
			doError(request, response, "launch.key.notfound",oauth_consumer_key, null);
			return;
		}
		OAuthMessage oam = OAuthServlet.getMessage(request, null);
		OAuthValidator oav = new SimpleOAuthValidator();
		OAuthConsumer cons = new OAuthConsumer("about:blank#OAuth+CallBack+NotUsed", oauth_consumer_key,oauth_secret, null);

		OAuthAccessor acc = new OAuthAccessor(cons);

		String base_string = null;
		try {
			base_string = OAuthSignatureMethod.getBaseString(oam);
		} catch (Exception e) {
			M_log.error(e.getLocalizedMessage(), e);
			base_string = null;
		}

		try {
			oav.validateMessage(oam, acc);
		} catch (Exception e) {
			M_log.warn("Provider failed to validate message");
			M_log.warn(e.getLocalizedMessage(), e);
			if (base_string != null) {
				M_log.warn(base_string);
			}
			doError(request, response, "launch.no.validate", context_id, null);
			return;
		}

		Session sess = SessionManager.getCurrentSession();
		if (sess == null) {
			doError(request, response, "launch.no.session", context_id, null);
			return;
		}

		// If we did not get first and last name, split lis_person_name_full
		String fullname = request.getParameter("lis_person_name_full");
		if (fname == null && lname == null && fullname != null) {
			int ipos = fullname.trim().lastIndexOf(' ');
			if (ipos == -1) {
				fname = fullname;
			} else {
				fname = fullname.substring(0, ipos);
				lname = fullname.substring(ipos + 1);
			}
		}

		// Setup role in the site. If trusted, we don't need this as the user already has a role in the site
		String userrole = null;
		if(!isTrustedConsumer) {
			userrole = request.getParameter("roles");
			if (userrole == null) {
				userrole = "";
			} else {
				userrole = userrole.toLowerCase();
			}
		}

		// Get the eid, if trusted get it from the user_id, otherwise construct it.
		String eid = null;
		if(isTrustedConsumer) {
			try {
				eid = UserDirectoryService.getUserEid(user_id);
			} catch (Exception e) {
				M_log.error(e.getLocalizedMessage(), e);
				doError(request, response, "launch.user.invalid", "user_id="+user_id, null);
				return;
			}
		} else {
			eid = oauth_consumer_key + ":" + user_id;
		}
		M_log.debug("eid="+eid);


		// If trusted consumer, login, otherwise check for existing user and create one if required
		if(isTrustedConsumer) {
			UsageSessionService.login(user_id, eid, ipAddress, null,UsageSessionService.EVENT_LOGIN_WS);
			sess.setUserId(user_id);
			sess.setUserEid(eid);
			
		} else {
			User user = null;

			try {
				user = UserDirectoryService.getUserByEid(eid);
			} catch (Exception e) {
				M_log.debug(e.getLocalizedMessage(), e);
				user = null;
			}

			if (user == null) {
				try {
					String hiddenPW = IdManager.createUuid();
					UserDirectoryService.addUser(null, eid, fname, lname,email, hiddenPW, "registered", null);
					M_log.info("Created user=" + eid);
					user = UserDirectoryService.getUserByEid(eid);
				} catch (Exception e) {
					doError(request, response, "launch.create.user", "user_id="+user_id, e);
					return;
				}

			}

			UsageSessionService.login(user.getId(), eid, ipAddress, null,UsageSessionService.EVENT_LOGIN_WS);
			sess.setUserId(user.getId());
			sess.setUserEid(user.getEid());
			
			// post the login event
			// eventTrackingService().post(eventTrackingService().newEvent(EVENT_LOGIN,
			// null, true));
		}

		// Get the site if it exists
		Site site = null;
		try {
			site = SiteService.getSite(siteId);
		} catch (Exception e) {
			M_log.debug(e.getLocalizedMessage(), e);
		}

		// If trusted and site does not exist, error, otherwise, create the site
		if(site == null) {
			if(isTrustedConsumer) {
				doError(request, response, "launch.site.invalid", "siteId="+siteId, null);
				return;
			} else {
				String context_type = request.getParameter("context_type");
				String sakai_type = "project";
				if (BasicLTIUtil.equalsIgnoreCase(context_type, "course")) {
					sakai_type = "course";
				}
				String context_title = request.getParameter("context_title");
				String context_label = request.getParameter("context_label");
				try {

					Site siteEdit = null;
					siteEdit = SiteService.addSite(siteId, sakai_type);
					if (BasicLTIUtil.isNotBlank(context_title)) {
						siteEdit.setTitle(context_title);
					}
					if (BasicLTIUtil.isNotBlank(context_label)) {
						siteEdit.setShortDescription(context_label);
					}
					siteEdit.setJoinable(false);
					siteEdit.setPublished(true);
					siteEdit.setPubView(false);
					siteEdit.setType(sakai_type);
					// record the original context_id to a site property
					siteEdit.getPropertiesEdit().addProperty("lti_context_id",context_id);
					saved = false;
					pushAdvisor();
					try {
						SiteService.save(siteEdit);
						M_log.info("Created  site=" + siteId + " label="+ context_label + " type=" + sakai_type + " title="+ context_title);
						saved = true;
					} catch (Exception e) {
						doError(request, response, "launch.site.save", "siteId="+siteId, e);
					} finally {
						popAdvisor();
					}
					if (!saved) {
						return;
					}
				} catch (Exception e) {
					doError(request, response, "launch.create.site", "siteId="+siteId, e);
					return;
				}
			}
		}
		
		// Check if the user is a member of the site already
		boolean userExistsInSite = false;
		try {
			site = SiteService.getSite(siteId);
			Member member = site.getMember(user_id);
			if(member != null && BasicLTIUtil.equals(member.getUserEid(), eid)) {
				userExistsInSite = true;
			}
		} catch (Exception e) {
			M_log.warn(e.getLocalizedMessage(), e);
			doError(request, response, "launch.site.invalid", "siteId="+siteId, e);
			return;
		}
		
		M_log.debug("userExistsInSite="+userExistsInSite);
		
		
		// If not a member of the site, and we are a trusted consumer, error
		// Otherwise, add them to the site
		if(!userExistsInSite) {
			if(isTrustedConsumer) {
				doError(request, response, "launch.site.user.missing", "user_id="+user_id + ", siteId="+siteId, null);
				return;
			} else {
				try {
					site = SiteService.getSite(siteId);
					Set<Role> roles = site.getRoles();
					String maintainRole = site.getMaintainRole();
					String joinerRole = site.getJoinerRole();
		
					for (Role r : roles) {
						String roleId = r.getId();
						if (maintainRole == null && (roleId.equalsIgnoreCase("maintain") || roleId.equalsIgnoreCase("instructor"))) {
							maintainRole = roleId;
						}
		
						if (joinerRole == null && (roleId.equalsIgnoreCase("access") || roleId.equalsIgnoreCase("student"))) {
							joinerRole = roleId;
						}
					}
		
					boolean isInstructor = userrole.indexOf("instructor") >= 0;
					String newRole = joinerRole;
					if (isInstructor && maintainRole != null)
						newRole = maintainRole;
		
					if (newRole == null) {
						M_log.warn("Could not find Sakai role, role=" + userrole+ " user=" + user_id + " site=" + siteId);
						doError(request, response, "launch.role.missing", "siteId="+siteId, null);
						return;
					}
		
					User theuser = UserDirectoryService.getUserByEid(eid);
		
					Role currentRoleObject = site.getUserRole(theuser.getId());
					String currentRole = null;
					if (currentRoleObject != null) {
						currentRole = currentRoleObject.getId();
					}
		
					if (!newRole.equals(currentRole)) {
						site.addMember(theuser.getId(), newRole, true, false);
						if (currentRole == null) {
							M_log.info("Added role=" + newRole + " user=" + user_id + " site=" + siteId + " LMS Role=" + userrole);
						} else {
							M_log.info("Old role=" + currentRole + " New role=" + newRole + " user=" + user_id + " site=" + siteId+ " LMS Role=" + userrole);
						}
		
						saved = false;
						pushAdvisor();
						try {
							SiteService.save(site);
							M_log.info("Site saved role=" + newRole + " user="+ user_id + " site=" + siteId);
							saved = true;
						} catch (Exception e) {
							doError(request, response, "launch.site.save", "siteId="+ siteId + " tool_id=" + tool_id, e);
						} finally {
							popAdvisor();
						}
						if (!saved) {
							return;
						}
					}
				} catch (Exception e) {
					M_log.warn("Could not add user to site role=" + userrole + " user="+ user_id + " site=" + siteId);
					M_log.warn(e.getLocalizedMessage(), e);
					doError(request, response, "launch.join.site", "siteId="+siteId, e);
					return;
				}
			}
		}
		
		
		// Check if the site already has the tool
		String toolPlacementId = null;
		try {
			site = SiteService.getSite(siteId);
			ToolConfiguration toolConfig = site.getToolForCommonId(tool_id);
			if(toolConfig != null) {
				toolPlacementId = toolConfig.getId();
			}
		} catch (Exception e) {
			M_log.warn(e.getLocalizedMessage(), e);
			doError(request, response, "launch.tool.search", "tool_id="+tool_id, e);
			return;
		}
		
		M_log.debug("toolPlacementId="+toolPlacementId);

		
		// If tool not in site, and we are a trusted consumer, error
		// Otherwise, add tool to the site
		ToolConfiguration toolConfig = null;
		if(BasicLTIUtil.isBlank(toolPlacementId)) {
			if(isTrustedConsumer) {
				doError(request, response, "launch.site.tool.missing", "tool_id="+tool_id + ", siteId="+siteId, null);
				return;
			} else {
				try {
					SitePage sitePageEdit = null;
					sitePageEdit = site.addPage();
					sitePageEdit.setTitle(tool_id);
					
					toolConfig = sitePageEdit.addTool();
					toolConfig.setTool(tool_id, ToolManager.getTool(tool_id));
					toolConfig.setTitle(tool_id);
					
					Properties propsedit = toolConfig.getPlacementConfig();
					propsedit.setProperty(BASICLTI_RESOURCE_LINK, resource_link_id);
					pushAdvisor();
					try {
						SiteService.save(site);
						M_log.info("Tool added, tool_id="+tool_id + ", siteId="+siteId);
					} catch (Exception e) {
						doError(request, response, "launch.site.save", "tool_id="+tool_id + ", siteId="+siteId, e);
						return;
					} finally {
						popAdvisor();
					}
					toolPlacementId = toolConfig.getId();
					
				} catch (Exception e) {
					doError(request, response, "launch.tool.add", "tool_id="+tool_id + ", siteId="+siteId, e);
					return;
				}
			}
		}
		
		// Get ToolConfiguration for tool if not already setup
		if(toolConfig == null){
			toolConfig =  site.getToolForCommonId(tool_id);
		}
		
		// Check user has access to this tool in this site
		// This will be incorporated into KNL-428 but is here until then.
		if(!isToolVisible(site, toolConfig)) {
			M_log.warn("Not allowed to access tool user_id=" + user_id + " site="+ siteId + " tool=" + tool_id);
			doError(request, response, "launch.site.tool.denied", "user_id=" + user_id + " site="+ siteId + " tool=" + tool_id, null);
			return;
		}
		
		
		// Construct a URL to this tool
		StringBuilder url = new StringBuilder();
			url.append(ServerConfigurationService.getServerUrl());
			url.append(ServerConfigurationService.getString("portalPath", "/portal"));
			url.append("/tool-reset/");
			url.append(toolPlacementId);
			url.append("?panel=Main");

		M_log.debug("url="+url.toString());
			
			
		//String toolLink = ServerConfigurationService.getPortalUrl()+ "/tool-reset/" + placement_id + "?panel=Main";
		// Compensate for bug in getPortalUrl()
		//toolLink = toolLink.replace("IMS BLTI Portlet", "portal");
		
		
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_FOUND);
		response.sendRedirect(url.toString());
		
		/*
		
		PrintWriter out = response.getWriter();
		out.println("<body><div style=\"text-align: center\">");
		out.println("&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>");
		out.println("&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>");
		out.println("<a href=\"" + url.toString() + "\">");
		out.println("<span id=\"hideme\">" + rb.getString("launch.continue")
				+ "</span>");
		out.println("</a>");
		out.println(" <script language=\"javascript\"> \n"
						+ "    document.getElementById(\"hideme\").style.display = \"none\";\n"
						+ "    location.href=\"" + url.toString() + "\";\n"
						+ " </script> \n");
		out.println("</div>");
		out.println("</body>");
		
		out.close();
		*/
		

	}

	public void destroy() {

	}
	
	/**
	 * Method to check if a tool is visible for a user in a site, based on KNL-428
	 * @param site
	 * @param toolConfig
	 * @return
	 */
	private boolean isToolVisible(Site site, ToolConfiguration toolConfig) {
		
		//no way to check, so allow access. It's then up to the tool to control permissions
		if(site == null || toolConfig == null) {
			return true;
		}
		
		String toolPermissionsStr = toolConfig.getConfig().getProperty("functions.require");
		if (M_log.isDebugEnabled()) {
			M_log.debug("tool: " + toolConfig.getToolId() + ", permissions: " + toolPermissionsStr);
		}

		//no special permissions required, it's visible
		if(BasicLTIUtil.isBlank(toolPermissionsStr)) {
			return true;
		}
		
		//check each set, if multiple permissions in the set, must have all.
		String[] toolPermissionsSets = toolPermissionsStr.split("\\|");
		for (int i = 0; i < toolPermissionsSets.length; i++){
			String[] requiredPermissions = toolPermissionsSets[i].split(","); 
			boolean allowed = true;
			for (int j = 0; j < requiredPermissions.length; j++) {
				//since all in a set are required, if we are missing just one permission, set false, break and continue to check next set
				//as that set may override and allow access
				if (!SecurityService.unlock(requiredPermissions[j].trim(), site.getReference())){
					allowed = false;
					return false;
				}
			}
			//if allowed, we have matched the entire set so are satisfied
			//otherwise we will check the next set
			if(allowed) {
				return true;
			}
		}
		
		//no sets were completely matched
		return false;
	}
	
	

}
