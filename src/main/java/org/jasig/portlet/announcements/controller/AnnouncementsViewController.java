/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portlet.announcements.controller;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.apache.log4j.Logger;
import org.jasig.portlet.announcements.UnauthorizedException;
import org.jasig.portlet.announcements.model.Announcement;
import org.jasig.portlet.announcements.model.AnnouncementSortStrategy;
import org.jasig.portlet.announcements.model.Topic;
import org.jasig.portlet.announcements.model.TopicSubscription;
import org.jasig.portlet.announcements.service.IAnnouncementService;
import org.jasig.portlet.announcements.service.ITopicSubscriptionService;
import org.jasig.portlet.announcements.service.UserPermissionChecker;
import org.jasig.portlet.announcements.service.UserPermissionCheckerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.portlet.PortletMode;
import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author eolsson
 *
 */
@Controller
public class AnnouncementsViewController implements InitializingBean {

    private static final String GUEST_USERNAME = "guest";
    private static final Logger logger = Logger.getLogger(AnnouncementsViewController.class);
    private Cache guestAnnouncementCache = null;

    @Autowired
    private ITopicSubscriptionService tss = null;

    @Autowired
    private final IAnnouncementService announcementService = null;

    @Autowired
    private CacheManager cm = null;

    @Autowired(required=true)
    private final IViewNameSelector viewNameSelector = null;

    @Autowired
    private final UserPermissionCheckerFactory userPermissionCheckerFactory = null;

    public static final String PREFERENCE_DISPLAY_STARTDATE = "AnnouncementsViewController.displayPublishDate";
    public static final String PREFERENCE_DISABLE_EDIT = "AnnouncementsViewController.PREFERENCE_DISABLE_EDIT";
    public static final String PREFERENCE_PAGE_SIZE = "AnnouncementsViewController.PAGE_SIZE";
    public static final String PREFERENCE_SORT_STRATEGY = "AnnouncementsViewController.AnnouncementSortStrategy";
    public static final String PREFERENCE_USE_SCROLLING_DISPLAY = "AnnouncementsViewController.useScrollingDisplay";
    public static final String PREFERENCE_SCROLLING_DISPLAY_HEIGHT_PIXELS = "AnnouncementsViewController.scrollingDisplayHeightPixels";
    public static final String PREFERENCE_HIDE_ABSTRACT = "AnnouncementsViewController.hideAbstract";
    public static final String DEFAULT_SORT_STRATEGY = "START_DISPLAY_DATE_ASCENDING";

    /**
     * Main method of this display controller. Calculates which topics should be shown to
     * this user and which announcements to show from those topics.
     * @param model
     * @param request
     * @param from
     * @param toimport java.util.Date;

     * @return
     * @throws PortletException
     */
    @SuppressWarnings("unchecked")
    @RequestMapping("VIEW")
    public String mainView(Model model, RenderRequest request,
            @RequestParam(value="from",required=false) Integer from,
            @RequestParam(value="to",required=false) Integer to)
        throws PortletException {

        if (from == null || to == null) {
            from = 0;
            to = (Integer) model.asMap().get("increment");
        }

        PortletPreferences prefs = request.getPreferences();

        List<Announcement> announcements;
        List<Announcement> emergencyAnnouncements;

        Element guestCacheElement = null;
        Element emergCacheElement = null;
        guestCacheElement = guestAnnouncementCache.get("guest");
        emergCacheElement = guestAnnouncementCache.get("emergency");

        final boolean isGuest = Boolean.valueOf((Boolean) model.asMap().get("isGuest"));
        if (!isGuest || (guestCacheElement == null || emergCacheElement == null)) {

            // create a new announcement list
            announcements = new ArrayList<Announcement>();
            emergencyAnnouncements = new ArrayList<Announcement>();

            // fetch the user's topic subscription from the database
            List<TopicSubscription> myTopics = tss.getTopicSubscription(request);

            // add all the published announcements of each subscribed topic to the announcement list
            // to emergency announcements into their own list
            for (TopicSubscription ts: myTopics) {
                if (ts.getSubscribed() && ts.getTopic().getSubscriptionMethod() != Topic.EMERGENCY) {
                    announcements.addAll(ts.getTopic().getPublishedAnnouncements());
                }
                else if (ts.getSubscribed() && ts.getTopic().getSubscriptionMethod() == Topic.EMERGENCY) {
                    emergencyAnnouncements.addAll(ts.getTopic().getPublishedAnnouncements());
                }
            }

            // sort the list (since they are not sorted from the database)
            Comparator<Announcement> sortStrategy = AnnouncementSortStrategy.getStrategy(prefs.getValue(PREFERENCE_SORT_STRATEGY,DEFAULT_SORT_STRATEGY));
            Collections.sort(announcements,sortStrategy);
            Collections.sort(emergencyAnnouncements,sortStrategy);

            if (isGuest) {
                if (logger.isDebugEnabled())
                    logger.debug("Guest cache expired. Regenerating guest cache.");

                guestAnnouncementCache.put(new Element("guest", announcements));
                guestAnnouncementCache.put(new Element("emergency", emergencyAnnouncements));
            }
        }
        else {
            // we're a guest and we're within the cache timeout period, so return the cached announcements
            if (logger.isDebugEnabled())
                logger.debug("Guest cache valid. Using guest cache.");
            announcements = (List<Announcement>) guestCacheElement.getObjectValue();
            emergencyAnnouncements = (List<Announcement>) emergCacheElement.getObjectValue();
        }

        // create a shortened list
        final boolean useScrollingDisplay = Boolean.valueOf((Boolean) model.asMap().get("useScrollingDisplay"));
        final List<Announcement> announcementsShort = useScrollingDisplay 
                ? announcements
                : paginateAnnouncements(announcements, from, to, model);

        // Disable the edit link where appropriate
        Boolean disableEdit = Boolean.valueOf(prefs.getValue(PREFERENCE_DISABLE_EDIT, "false"));
        model.addAttribute("disableEdit", disableEdit);

        model.addAttribute("from", new Integer(from));
        model.addAttribute("to", new Integer(to));
        model.addAttribute("hasMore", (!useScrollingDisplay && announcements.size() > to));
        model.addAttribute("announcements", announcementsShort);
        model.addAttribute("emergency", emergencyAnnouncements);
        model.addAttribute("hideAbstract", Boolean.valueOf(prefs.getValue(PREFERENCE_HIDE_ABSTRACT,"false")));
        return viewNameSelector.select(request, "displayAnnouncements");
    }

    @RequestMapping(value="VIEW",params="action=displayFullAnnouncement")
    public String displayFullAnnouncement(Model model, RenderRequest request,
            @RequestParam("announcementId") String announcementId) throws Exception {

        Announcement announcement = getAnnouncementById(request,announcementId);
        model.addAttribute("announcement", announcement);

        return viewNameSelector.select(request, "displayFullAnnouncement");
    }

    @RequestMapping("EDIT")
    public String editPreferences(Model model, RenderRequest request) throws PortletException {

        PortletPreferences prefs = request.getPreferences();
        List<TopicSubscription> myTopics = tss.getTopicSubscriptionEdit(request);

        if (request.getRemoteUser() == null ||
                request.getRemoteUser().equalsIgnoreCase(GUEST_USERNAME)) {
            model.addAttribute("isGuest", Boolean.TRUE);
        } else {
            model.addAttribute("isGuest", Boolean.FALSE);
        }
        model.addAttribute("topicSubscriptions", myTopics);
        model.addAttribute("topicsToUpdate", myTopics.size());
        model.addAttribute("prefHideAbstract",Boolean.valueOf(prefs.getValue(PREFERENCE_HIDE_ABSTRACT,"false")));
        return viewNameSelector.select(request, "editDisplayPreferences");
    }

    @RequestMapping("EDIT")
    public void savePreferences(ActionRequest request, ActionResponse response,
            @RequestParam("topicsToUpdate") Integer topicsToUpdate) throws PortletException,IOException {

        PortletPreferences prefs = request.getPreferences();
        List<TopicSubscription> newSubscription = new ArrayList<TopicSubscription>();

        for (int i=0; i<topicsToUpdate; i++) {
            Long topicId = Long.valueOf( request.getParameter("topicId_"+i) );

            // Will be numeric for existing, persisted TopicSubscription
            // instances;  blank (due to null id field) otherwise
            String topicSubId = request.getParameter("topicSubId_"+i).trim();

            Boolean subscribed = Boolean.valueOf( request.getParameter("subscribed_"+i) );
            Topic topic = announcementService.getTopic(topicId);

            // Make sure that any pushed_forced topics weren't sneakingly removed (by tweaking the URL, for example)
            if (topic.getSubscriptionMethod() == Topic.PUSHED_FORCED) {
                subscribed = new Boolean(true);
            }

            TopicSubscription ts = new TopicSubscription(request.getRemoteUser(), topic, subscribed);
            if (topicSubId.length() > 0) {
                // This TopicSubscription represents an existing, persisted entity
                try {
                    ts.setId(Long.valueOf(topicSubId));
                } catch (NumberFormatException nfe) {
                    logger.debug(nfe.getMessage(), nfe);
                }
            }

            newSubscription.add(ts);
        }

        if (newSubscription.size() > 0) {
            try {
                announcementService.addOrSaveTopicSubscription(newSubscription);
            } catch (Exception e) {
                logger.error("ERROR saving TopicSubscriptions for user "+request.getRemoteUser()+". Message: "+e.getMessage());
            }
        }

        String hideAbstract = Boolean.valueOf(request.getParameter("hideAbstract")).toString();
        prefs.setValue(PREFERENCE_HIDE_ABSTRACT,hideAbstract);
        prefs.store();

        response.setPortletMode(PortletMode.VIEW);
        response.setRenderParameter("action", "displayAnnouncements");

    }

    @ModelAttribute("displayPublishDate")
    public boolean getDisplayPublishDate(PortletRequest req) {
        PortletPreferences prefs = req.getPreferences();
        return Boolean.parseBoolean(prefs.getValue(PREFERENCE_DISPLAY_STARTDATE, "false"));
    }

    public void setTss(ITopicSubscriptionService tss) {
        this.tss = tss;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        guestAnnouncementCache = cm.getCache("guestAnnouncementCache");
        if (guestAnnouncementCache == null) {
            throw new BeanCreationException("Required guestAnnouncementCache could not be loaded.");
        }
        else {
            logger.debug("guestAnnouncementCache created.");
        }
    }

    public void setCm(CacheManager cm) {
        this.cm = cm;
    }

  	@RequestMapping(value = "VIEW", params = "action=displayFullAnnouncementHistory")
  	public String displayFullAnnouncementHistory(Model model,
  			RenderRequest request,
  			@RequestParam("announcementId") String announcementId)
  			throws Exception {

  		Announcement announcement = getAnnouncementById(request,announcementId);
  		model.addAttribute("announcement", announcement);

  		return viewNameSelector.select(request, "displayFullAnnouncementHistory");
  	}

  	@RequestMapping(value = "VIEW", params = "action=displayHistory")
  	public String displayHistory(Model model, RenderRequest request)
  			throws Exception {

  		List<Announcement> announcements = new ArrayList<Announcement>();

  		// fetch the user's topic subscription from the database
  		List<TopicSubscription> myTopics = tss.getTopicSubscription(request);

  		// add all the published announcements of each subscribed topic to the
  		// announcement list
  		for (TopicSubscription ts : myTopics) {
  			if (ts.getSubscribed() && ts.getTopic().getSubscriptionMethod() != Topic.EMERGENCY) {
  				announcements.addAll(ts.getTopic().getHistoricAnnouncements());
  			}
  		}

  		// sort the list by end display date descending (since they are not
  		// sorted from the database)
  		Collections.sort(announcements, new Comparator<Announcement>() {
  			@Override
            public int compare(Announcement s, Announcement s2) {
  				return s2.getEndDisplay().compareTo(s.getEndDisplay());
  			}

  		});

  		model.addAttribute("announcements", announcements);

  		return viewNameSelector.select(request, "displayHistory");
  	}
  	
  	@ModelAttribute("increment")
  	public int getPageSize(PortletRequest req) {
        final PortletPreferences prefs = req.getPreferences();
        int rslt = 5;  // default
        try {
            rslt = Integer.parseInt(prefs.getValue(PREFERENCE_PAGE_SIZE, "5"));
        } catch (NumberFormatException nfe) {
            // Log it, but roll on...
            logger.warn("Non-integer value encountered for " 
                    + PREFERENCE_PAGE_SIZE + ": " 
                    + prefs.getValue(PREFERENCE_PAGE_SIZE, null));
        }
        return rslt;
  	}
  	
  	@ModelAttribute("isGuest")
  	public boolean isGuest(PortletRequest req) {
        boolean rslt = (req.getRemoteUser() == null || req.getRemoteUser().equalsIgnoreCase(GUEST_USERNAME));
        logger.debug("isGuest is: "+Boolean.toString(rslt));
        logger.debug("remoteUser is: "+req.getRemoteUser());
        return rslt;
  	}
  	
  	@ModelAttribute("useScrollingDisplay")
  	public boolean getUseScrollingDisplay(PortletRequest req) {
  	  final PortletPreferences prefs = req.getPreferences();
  	    return Boolean.valueOf(prefs.getValue(PREFERENCE_USE_SCROLLING_DISPLAY, "false"));  // default is false
  	}
  	
    @ModelAttribute("scrollingDisplayHeightPixels")
    public int getScrollingDisplayHeightPixels(PortletRequest req) {
        final PortletPreferences prefs = req.getPreferences();
        int rslt = 500;  // default
        try {
            rslt = Integer.parseInt(prefs.getValue(PREFERENCE_SCROLLING_DISPLAY_HEIGHT_PIXELS, "500"));
        } catch (NumberFormatException nfe) {
            // Log it, but roll on...
            logger.warn("Non-integer value encountered for " 
                    + PREFERENCE_SCROLLING_DISPLAY_HEIGHT_PIXELS + ": " 
                    + prefs.getValue(PREFERENCE_SCROLLING_DISPLAY_HEIGHT_PIXELS, null));
        }
        return rslt;
    }

    /*
  	 * Implementation
  	 */
    
    private List<Announcement> paginateAnnouncements(final List<Announcement> announcements, Integer from, Integer to, Model model) {
        List<Announcement> rslt;
        // if the announcement list is already short, then just reference it
        if (announcements.size() < to - from) {
            rslt = announcements;
        }
        // otherwise, just take the range requested and pass it along to the view
        else {
            rslt = new ArrayList<Announcement>();
            for (int i=from; i<to && announcements.size() > i; i++) {
                if (announcements.get(i) != null) {
                    rslt.add(announcements.get(i));
                }
            }
        }
        return rslt;
    }

    private Announcement getAnnouncementById(PortletRequest request, String announcementId) throws Exception {
        Long annId = Long.valueOf(announcementId);
        Announcement announcement = announcementService.getAnnouncement(annId);

        if (!UserPermissionChecker.inRoleForTopic(request, UserPermissionChecker.AUDIENCE_ROLE_NAME, announcement.getParent())) {
            throw new UnauthorizedException();
        }

        return announcement;
    }
}
