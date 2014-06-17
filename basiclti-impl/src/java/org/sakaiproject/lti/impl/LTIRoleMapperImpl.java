package org.sakaiproject.lti.impl;

import java.util.Map;
import java.util.AbstractMap;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.imsglobal.basiclti.BasicLTIConstants;
import org.imsglobal.basiclti.BasicLTIUtil;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.lti.api.LTIException;
import org.sakaiproject.lti.api.LTIRoleMapper;
import org.sakaiproject.user.api.User;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;


/**
 *  @author Adrian Fish <a.fish@lancaster.ac.uk>
 */
public class LTIRoleMapperImpl implements LTIRoleMapper {

	private static Log M_log = LogFactory.getLog(LTIRoleMapperImpl.class);

    /**
     *  Injected from Spring, see components.xml
     */
    private SiteService siteService = null;
    public void setSiteService(SiteService siteService) {
        this.siteService = siteService;
    }

    public Map.Entry<String, String> mapLTIRole(Map payload, User user, Site site, boolean trustedConsumer) throws LTIException {

        String ltiRole = null;

        if (!trustedConsumer) {
            ltiRole = (String) payload.get(BasicLTIConstants.ROLES);
            if (ltiRole == null) {
                ltiRole = "";
            } else {
                ltiRole = ltiRole.toLowerCase();
            }
        }

        if (M_log.isDebugEnabled()) {
            M_log.debug("ltiRole=" + ltiRole);
        }

        // Check if the user is a member of the site already
        boolean userExistsInSite = false;
        try {
            Member member = site.getMember(user.getId());
            if (member != null && BasicLTIUtil.equals(member.getUserEid(), user.getEid())) {
                userExistsInSite = true;
            }
        } catch (Exception e) {
            M_log.warn(e.getLocalizedMessage(), e);
            throw new LTIException( "launch.site.invalid", "siteId="+site.getId(), e);
        }

        if (M_log.isDebugEnabled()) {
            M_log.debug("userExistsInSite=" + userExistsInSite);
        }

        // If not a member of the site, and we are a trusted consumer, error
        // Otherwise, add them to the site
        if (!userExistsInSite && trustedConsumer) {
            throw new LTIException( "launch.site.user.missing", "user_id="+user.getId()+ ", siteId="+site.getId(), null);
        }

        try {
            site = siteService.getSite(site.getId());
            Set<Role> roles = site.getRoles();

            //BLTI-151 see if we can directly map the incoming role to the list of site roles
            String newRole = null;
            if (M_log.isDebugEnabled()) {
                M_log.debug("Incoming ltiRole:" + ltiRole);
            }
            for (Role r : roles) {
                String roleId = r.getId();

                if (BasicLTIUtil.equalsIgnoreCase(roleId, ltiRole)) {
                    newRole = roleId;
                    if (M_log.isDebugEnabled()) {
                        M_log.debug("Matched incoming role to role in site:" + roleId);
                    }
                    break;
                }
            }

            //if we haven't mapped a role, check against the standard roles and fallback
            if (BasicLTIUtil.isBlank(newRole)) {

                if (M_log.isDebugEnabled()) {
                    M_log.debug("No match, falling back to determine role");
                }

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

                boolean isInstructor = ltiRole.indexOf("instructor") >= 0;
                newRole = joinerRole;
                if (isInstructor && maintainRole != null) {
                    newRole = maintainRole;
                }

                if (M_log.isDebugEnabled()) {
                    M_log.debug("Determined newRole as: " + newRole);
                }
            }
            if (newRole == null) {
                M_log.warn("Could not find Sakai role, role=" + ltiRole+ " user=" + user.getId() + " site=" + site.getId());
                throw new LTIException( "launch.role.missing", "siteId="+site.getId(), null);

            }

            return new AbstractMap.SimpleImmutableEntry(ltiRole, newRole);
        } catch (Exception e) {
            M_log.warn("Could not map role role=" + ltiRole + " user="+ user.getId() + " site=" + site.getId());
            M_log.warn(e.getLocalizedMessage(), e);
            throw new LTIException( "map.role", "siteId="+site.getId(), e);
        }
    }
}
