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
import java.util.HashMap;
import java.util.Iterator;
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

	public void doError(HttpServletRequest request,
			HttpServletResponse response, String s, String message, Exception e)
			throws java.io.IOException {
		if (e != null) {
			M_log.error(e.getLocalizedMessage(), e);
		}
		M_log.info(rb.getString(s));
		String return_url = request
				.getParameter("launch_presentation_return_url");
		if (return_url != null && return_url.length() > 1) {
			if (return_url.indexOf('?') > 1) {
				return_url += "&lti_msg=" + URLEncoder.encode(rb.getString(s));
			} else {
				return_url += "?lti_msg=" + URLEncoder.encode(rb.getString(s));
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

	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	protected void doPost(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
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
		if (!"basic-lti-launch-request".equals(request
				.getParameter("lti_message_type"))
				|| !"LTI-1p0".equals(request.getParameter("lti_version"))
				|| oauth_consumer_key == null || resource_link_id == null) {
			doError(request, response, "launch.missing", null, null);
			return;
		}

		// Check the Tool ID
		String tool_id = request.getPathInfo();
		if (tool_id == null) {
			doError(request, response, "launch.tool_id.required", null, null);
			return;
		}

		// Trim off the leading slash and any trailing space
		tool_id = tool_id.substring(1).trim();
		String allowedTools = ServerConfigurationService.getString(
				"imsblti.provider.allowedtools", null);
		if (allowedTools != null && allowedTools.indexOf(tool_id) < 0) {
			doError(request, response, "launch.tool.notallowed", tool_id, null);
			return;
		}
		Tool toolCheck = ToolManager.getTool(tool_id);
		if (toolCheck == null) {
			doError(request, response, "launch.tool.notfound", tool_id, null);
			return;
		}

		// Contextualize the context_id with the OAuth consumer key
		// Also use the resource_link_id for the context_id if we did not
		// get a context_id
		if (context_id == null) {
			context_id = "res:" + resource_link_id;
		}
		String siteId = oauth_consumer_key + ":" + context_id;
		siteId = ShaUtil.sha1Hash(siteId);

		// Lookup the secret
		String configPrefix = "imsblti.provider." + oauth_consumer_key + ".";
		String oauth_secret = ServerConfigurationService.getString(configPrefix
				+ "secret", null);
		if (oauth_secret == null) {
			doError(request, response, "launch.key.notfound",
					oauth_consumer_key, null);
			return;
		}
		OAuthMessage oam = OAuthServlet.getMessage(request, null);
		OAuthValidator oav = new SimpleOAuthValidator();
		OAuthConsumer cons = new OAuthConsumer(
				"about:blank#OAuth+CallBack+NotUsed", oauth_consumer_key,
				oauth_secret, null);

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
			if (base_string != null)
				M_log.warn(base_string);
			doError(request, response, "launch.no.validate", context_id, null);
			return;
		}

		Session sess = SessionManager.getCurrentSession();
		if (sess == null) {
			doError(request, response, "launch.no.session", context_id, null);
			return;
		}

		// If we did not get first and last name, split name_full
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

		String userrole = request.getParameter("roles");
		if (userrole == null)
			userrole = "";
		userrole = userrole.toLowerCase();

		// Construct the eid
		String eid = null;
		if (user_id != null) {
			eid = oauth_consumer_key + ":" + user_id;
		}

		// Create the User's account if it does not exist
		if (eid != null) {

			User user = null;

			try {
				user = UserDirectoryService.getUserByEid(eid);
			} catch (Exception e) {
				M_log.error(e.getLocalizedMessage(), e);
				user = null;
			}

			if (user == null) {
				try {
					String hiddenPW = IdManager.createUuid();
					UserDirectoryService.addUser(null, eid, fname, lname,
							email, hiddenPW, "registered", null);
					M_log.info("Created user=" + eid);
					user = UserDirectoryService.getUserByEid(eid);
				} catch (Exception e) {
					doError(request, response, "launch.create.user", "context="
							+ context_id + " user=" + user_id, e);
					return;
				}

			}

			UsageSessionService.login(user.getId(), eid, ipAddress, null,
					UsageSessionService.EVENT_LOGIN_WS);
			sess.setUserId(user.getId());
			sess.setUserEid(user.getEid());

			// post the login event
			// eventTrackingService().post(eventTrackingService().newEvent(EVENT_LOGIN,
			// null, true));
		}

		// Load the site based on the context_id if it exists
		Site thesite = null;
		try {
			thesite = SiteService.getSite(siteId);
		} catch (Exception e) {
			M_log.error(e.getLocalizedMessage(), e);
			thesite = null;
		}

		// Create the site if it does not exist
		if (thesite == null) {
			String context_type = request.getParameter("context_type");
			String sakai_type = "project";
			if (context_type != null
					&& context_type.toLowerCase().indexOf("course") > -1) {
				sakai_type = "course";
			}
			String context_title = request.getParameter("context_title");
			String context_label = request.getParameter("context_label");
			try {

				Site siteEdit = null;
				siteEdit = SiteService.addSite(siteId, sakai_type);
				if (context_title != null)
					siteEdit.setTitle(context_title);
				if (context_label != null)
					siteEdit.setShortDescription(context_label);
				siteEdit.setJoinable(false);
				siteEdit.setPublished(true);
				siteEdit.setPubView(false);
				siteEdit.setType(sakai_type);
				// record the original context_id to a site property
				siteEdit.getPropertiesEdit().addProperty("lti_context_id",
						context_id);
				saved = false;
				pushAdvisor();
				try {
					SiteService.save(siteEdit);
					M_log.info("Created  site=" + siteId + " label="
							+ context_label + " type=" + sakai_type + " title="
							+ context_title);
					saved = true;
				} catch (Exception e) {
					doError(request, response, "launch.site.save", "site="
							+ siteId + " tool=" + tool_id, e);
				} finally {
					popAdvisor();
				}
				if (!saved)
					return;
			} catch (Exception e) {
				doError(request, response, "launch.create.site", siteId, e);
				return;
			}
		}

		// Add the current user to the site with the proper role
		try {
			thesite = SiteService.getSite(siteId);
			Set<Role> roles = thesite.getRoles();
			String maintainRole = thesite.getMaintainRole();
			String joinerRole = thesite.getJoinerRole();

			for (Role r : roles) {
				String roleId = r.getId();
				if (maintainRole == null
						&& (roleId.equalsIgnoreCase("maintain") || roleId
								.equalsIgnoreCase("instructor"))) {
					maintainRole = roleId;
				}

				if (joinerRole == null
						&& (roleId.equalsIgnoreCase("access") || roleId
								.equalsIgnoreCase("student"))) {
					joinerRole = roleId;
				}
			}

			boolean isInstructor = userrole.indexOf("instructor") >= 0;
			String newRole = joinerRole;
			if (isInstructor && maintainRole != null)
				newRole = maintainRole;

			if (newRole == null) {
				M_log.warn("Could not find Sakai role role=" + userrole
						+ " user=" + user_id + " site=" + siteId);
				doError(request, response, "launch.role.missing", siteId, null);
				return;
			}

			User theuser = UserDirectoryService.getUserByEid(eid);

			Role currentRoleObject = thesite.getUserRole(theuser.getId());
			String currentRole = null;
			if (currentRoleObject != null) {
				currentRole = currentRoleObject.getId();
			}

			if (!newRole.equals(currentRole)) {
				thesite.addMember(theuser.getId(), newRole, true, false);
				if (currentRole == null) {
					M_log.info("Added role=" + newRole + " user=" + user_id
							+ " site=" + siteId + " LMS Role=" + userrole);
				} else {
					M_log.info("Old role=" + currentRole + " New role="
							+ newRole + " user=" + user_id + " site=" + siteId
							+ " LMS Role=" + userrole);
				}

				saved = false;
				pushAdvisor();
				try {
					SiteService.save(thesite);
					M_log.info("Site saved role=" + newRole + " user="
							+ user_id + " site=" + siteId);
					saved = true;
				} catch (Exception e) {
					doError(request, response, "launch.site.save", "site="
							+ siteId + " tool=" + tool_id, e);
				} finally {
					popAdvisor();
				}
				if (!saved)
					return;
			}
		} catch (Exception e) {
			M_log.warn("Could not add user to site role=" + userrole + " user="
					+ user_id + " site=" + siteId);
			M_log.warn(e.getLocalizedMessage(), e);
			doError(request, response, "launch.join.site", siteId, e);
			return;
		}

		// See if we already have created the tool
		String placement_id = null;
		try {

			List pageEdits = thesite.getPages();
			for (Iterator i = pageEdits.iterator(); i.hasNext();) {
				SitePage pageEdit = (SitePage) i.next();
				List toolEdits = pageEdit.getTools();
				for (Iterator j = toolEdits.iterator(); j.hasNext();) {
					ToolConfiguration tool = (ToolConfiguration) j.next();
					Tool t = tool.getTool();
					if (!tool_id.equals(t.getId()))
						continue;
					Properties propsedit = tool.getPlacementConfig();
					String rli = propsedit.getProperty(BASICLTI_RESOURCE_LINK,
							null);
					if (resource_link_id.equals(rli)) {
						placement_id = tool.getId();
						break;
					}
				}
				if (placement_id != null)
					break;
			}

		} catch (Exception e) {
			doError(request, response, "launch.tool.search", "site:" + siteId
					+ " tool=" + tool_id, e);
			return;
		}

		// If the tool is not in the site, add the tool
		if (placement_id == null) {
			try {
				SitePage sitePageEdit = null;
				sitePageEdit = thesite.addPage();
				sitePageEdit.setTitle(tool_id);
				ToolConfiguration tool = sitePageEdit.addTool();
				Tool t = tool.getTool();

				tool.setTool(tool_id, ToolManager.getTool(tool_id));
				tool.setTitle(tool_id);
				Properties propsedit = tool.getPlacementConfig();
				propsedit.setProperty(BASICLTI_RESOURCE_LINK, resource_link_id);
				pushAdvisor();
				saved = false;
				try {
					SiteService.save(thesite);
					M_log.info("Tool added site=" + siteId + " tool_id="
							+ tool_id);
					saved = true;
				} catch (Exception e) {
					doError(request, response, "launch.site.save", "site:"
							+ siteId + " tool=" + tool_id, e);
				} finally {
					popAdvisor();
				}
				if (!saved)
					return;
				placement_id = tool.getId();
			} catch (Exception e) {
				doError(request, response, "launch.tool.add", "site:" + siteId
						+ " tool=" + tool_id, e);
				return;
			}
		}

		String toolLink = ServerConfigurationService.getPortalUrl()
				+ "/tool-reset/" + placement_id + "?panel=Main";
		// Compensate for bug in getPortalUrl()
		toolLink = toolLink.replace("IMS BLTI Portlet", "portal");

		response.setContentType("text/html");

		PrintWriter out = response.getWriter();
		out.println("<body><div style=\"text-align: center\">");
		out.println("&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>");
		out.println("&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>&nbsp;<br/>");
		out.println("<a href=\"" + toolLink + "\">");
		out.println("<span id=\"hideme\">" + rb.getString("launch.continue")
				+ "</span>");
		out.println("</a>");
		out
				.println(" <script language=\"javascript\"> \n"
						+ "    document.getElementById(\"hideme\").style.display = \"none\";\n"
						+ "    location.href=\"" + toolLink + "\";\n"
						+ " </script> \n");
		out.println("</div>");
		out.println("</body>");
		out.close();

	}

	public void destroy() {

	}

}
