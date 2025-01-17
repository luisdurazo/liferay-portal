<%--
/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
--%>

<%@ include file="/init.jsp" %>

<%
SiteNavigationMenuItem siteNavigationMenuItem = (SiteNavigationMenuItem)request.getAttribute(SiteNavigationWebKeys.SITE_NAVIGATION_MENU_ITEM);

String target = "_self";
String url = StringPool.BLANK;

if (siteNavigationMenuItem != null) {
	UnicodeProperties typeSettingsProperties = new UnicodeProperties();

	typeSettingsProperties.fastLoad(siteNavigationMenuItem.getTypeSettings());

	target = typeSettingsProperties.getProperty("target", "_self");
	url = typeSettingsProperties.getProperty("url");
}
%>

<aui:input autoFocus="<%= true %>" defaultLanguageId="<%= LocaleUtil.toLanguageId(themeDisplay.getSiteDefaultLocale()) %>" label="name" localized="<%= true %>" maxlength='<%= ModelHintsUtil.getMaxLength(SiteNavigationMenuItem.class.getName(), "name") %>' name="name" placeholder="name" type="text" value='<%= SiteNavigationMenuItemUtil.getSiteNavigationMenuItemXML(siteNavigationMenuItem, "name") %>'>
	<aui:validator name="required" />
</aui:input>

<aui:input label="url" name="TypeSettingsProperties--url--" placeholder="http://" value="<%= url %>">
	<aui:validator name="required" />

	<aui:validator name="url" />
</aui:input>

<aui:select label="target" name="TypeSettingsProperties--target--" value="<%= target %>">
	<aui:option label="blank" selected='<%= Objects.equals(target, "_blank") %>' value="_blank" />
	<aui:option label="parent" selected='<%= Objects.equals(target, "_parent") %>' value="_parent" />
	<aui:option label="self" selected='<%= Objects.equals(target, "_self") %>' value="_self" />
	<aui:option label="top" selected='<%= Objects.equals(target, "_top") %>' value="_top" />
</aui:select>