<%@ include file="/WEB-INF/jsp/include.jsp" %>
<link href="<c:url value="/css/baseAdmin.css"/>" rel="stylesheet" type="text/css" />

<div id="<portlet:namespace/>">
<div class="announcements-portlet-toolbar">
    <a style="text-decoration:none;font-size:0.9em;" href="<portlet:renderURL portletMode="view"><portlet:param name="action" value="showTopic"/><portlet:param name="topicId" value="${ topic.id }"/></portlet:renderURL>"><img src="<c:url value="/icons/arrow_left.png"/>" border="0" height="16" width="16" style="vertical-align:middle"/> Back to topic</a>
    <div class="announcements-portlet-secondary">
	    <a style="text-decoration:none;font-size:0.9em;" href="<portlet:renderURL portletMode="view" windowState="normal"></portlet:renderURL>"><img src="<c:url value="/icons/house.png"/>" border="0" height="16" width="16" style="vertical-align:middle"/> <spring:message code="general.adminhome"/></a>
	</div>
</div>
<div class="portlet-section-header">
	<h2 class="title" role="heading">History</h2>
</div>
<table width="100%" cellspacing="0" cellpadding="0" id="historyTable" class="tablesorter">
<thead><tr><th>Topic</th><th>Announcement</th><th>Start Date</th><th>End Date</th><td>Repost</td><td>Delete</td></tr></thead>
<tbody>
<c:forEach items="${announcements}" var="ann">
	<tr>
		<td><c:out value="${ann.parent.title}"/></td>
		<td><a title="Click to Preview"  href="<portlet:renderURL><portlet:param name="action" value="previewAnnouncement"/><portlet:param name="annId" value="${ ann.id }"/></portlet:renderURL>"><c:out value="${ann.title}"/></a><br /><c:out value="${ann.abstractText}"/></td>
		<td><fmt:formatDate value="${ann.startDisplay}" dateStyle="short"/></td>
		<td><fmt:formatDate value="${ann.endDisplay}" dateStyle="short"/></td>
		<td><a href="<portlet:renderURL><portlet:param name="action" value="addAnnouncement"/><portlet:param name="editId" value="${ann.id}"/></portlet:renderURL>" title="<spring:message code="show.viewedit"/>"><img alt="<spring:message code="show.viewedit"/>" src="<c:url value="/icons/pencil.png"/>" border="0" height="16" width="16"/></a></td>
		<td><a href="#" onclick="<portlet:namespace/>_delete('<portlet:actionURL><portlet:param name="action" value="deleteAnnouncement"/><portlet:param name="annId" value="${ann.id}"/><portlet:param name="topicId" value="${topic.id}"/></portlet:actionURL>');" title="<spring:message code="show.delete"/>"><img border="0" alt="<spring:message code="show.delete"/>" src="<c:url value="/icons/bin_empty.png"/>" height="16" width="16"/></a></td>
	</tr>
</c:forEach>
</tbody>
</table>



<script type="text/javascript" src="https://webresources.uchicago.edu/js/jquery-1.3.2.min.js"></script>
<script type="text/javascript" src="https://webresources.uchicago.edu/js/jquery.tablesorter.min.js"></script>
<script type="text/javascript">
var <portlet:namespace/>  = jQuery.noConflict(true);
<portlet:namespace/>(document).ready(function(){
		<portlet:namespace/>("#historyTable").tablesorter( {sortList: [[1,0], [2,0]]} );
	}
);
</script>

</div>