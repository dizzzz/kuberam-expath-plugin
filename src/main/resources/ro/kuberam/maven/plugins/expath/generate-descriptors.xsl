﻿<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://expath.org/ns/pkg"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:pkg="http://expath.org/ns/pkg" xmlns:exist-pkg="http://exist-db.org/ns/expath-pkg"
	exclude-result-prefixes="pkg exist-pkg" version="2.0">

	<xsl:output method="xml" indent="yes" omit-xml-declaration="no" />

	<xsl:param name="package-dir" as="xs:anyURI" />
	<xsl:variable name="package-type" select="/*/pkg:type" />
	<xsl:variable name="package-version" select="/*/@version" />

	<xsl:variable name="abbrev" select="/*/@abbrev" />
	<xsl:variable name="name" select="/*/@name" />
	<xsl:variable name="title" select="/*/pkg:title" />
	<xsl:variable name="authors" select="/*/pkg:author" />
	<xsl:variable name="components"
		select="collection(concat($package-dir, '?select=components.xml'))/element()" />
	<xsl:variable name="exist-components"
		select="collection(concat($package-dir, '?select=exist-components.xml'))/element()" />

	<xsl:template match="/">
		<xsl:choose>
			<xsl:when
				test="/*/*[local-name() = 'dependency' and @processor = 'http://exist-db.org']">
				<!-- generate exist.xml -->
				<xsl:result-document href="{concat($package-dir, '/exist.xml')}">
					<package xmlns="http://exist-db.org/ns/expath-pkg">
						<xsl:copy-of select="$exist-components/*" />
						<xsl:for-each select="$components/element()">
							<xsl:choose>
								<xsl:when test="ends-with(element()[2], '.jar')">
									<jar>
										<xsl:value-of select="substring(element()[2], 9)" />
									</jar>
								</xsl:when>
							</xsl:choose>
						</xsl:for-each>
					</package>
				</xsl:result-document>
			</xsl:when>
		</xsl:choose>

		<!-- generate cxan.xml -->
		<xsl:result-document href="{concat($package-dir, '/cxan.xml')}">
			<package xmlns="http://cxan.org/ns/package" id="{$abbrev}"
				name="{$name}" version="{$package-version}">
				<xsl:for-each select="$authors">
					<author id="{@id}">
						<xsl:value-of select="." />
					</author>
				</xsl:for-each>
				<xsl:for-each select="/*/pkg:category">
					<category>
						<xsl:value-of select="." />
					</category>
				</xsl:for-each>
				<xsl:for-each select="/*/pkg:tag">
					<tag>
						<xsl:value-of select="." />
					</tag>
				</xsl:for-each>
			</package>
		</xsl:result-document>

		<!-- generate expath-pkg.xml -->
		<xsl:result-document href="{concat($package-dir, '/expath-pkg.xml')}">
			<package xmlns="http://expath.org/ns/pkg" name="{$name}"
				abbrev="{$abbrev}" version="{$package-version}" spec="1.0">
				<xsl:copy-of select="$title" />
				<xsl:if test="/*/pkg:website">
					<home>
						<xsl:value-of select="/*/pkg:website" />
					</home>
				</xsl:if>
				<xsl:copy-of select="/*/pkg:dependency" />
				<xsl:for-each select="$components//exist-pkg:xquery">
					<xquery>
						<namespace>
							<xsl:value-of select="exist-pkg:namespace" />
						</namespace>
						<file>
							<xsl:value-of select="exist-pkg:file" />
						</file>
					</xquery>
				</xsl:for-each>
			</package>
		</xsl:result-document>

		<!-- generate repo.xml -->
		<xsl:result-document href="{concat($package-dir, '/repo.xml')}">
			<meta xmlns="http://exist-db.org/xquery/repo" xmlns:repo="http://exist-db.org/xquery/repo">
				<description>
					<xsl:value-of select="$title" />
				</description>
				<xsl:for-each select="$authors">
					<author id="{@id}">
						<xsl:value-of select="." />
					</author>
				</xsl:for-each>
				<website>
					<xsl:value-of select="/*/pkg:website" />
				</website>
				<status>
					<xsl:value-of select="/*/pkg:status" />
				</status>
				<license>
					<xsl:value-of select="/*/pkg:license" />
				</license>
				<copyright>
					<xsl:value-of select="/*/pkg:copyright" />
				</copyright>
				<type>
					<xsl:value-of select="$package-type" />
				</type>
				<xsl:if test="$package-type = 'application'">
					<target>
						<xsl:value-of select="/*/pkg:target" />
					</target>
				</xsl:if>
				<xsl:if test="/*/pkg:prepare">
					<prepare>
						<xsl:value-of select="/*/pkg:prepare" />
					</prepare>
				</xsl:if>
				<xsl:if test="/*/pkg:finish">
					<finish>
						<xsl:value-of select="/*/pkg:finish" />
					</finish>
				</xsl:if>
				<xsl:if test="/*/pkg:permissions">
					<permissions>
						<xsl:copy-of select="/*/pkg:permissions/@*" />
					</permissions>
				</xsl:if>
				<xsl:if test="/*/pkg:note">
					<note>
						<xsl:value-of select="/*/pkg:note" />
					</note>
				</xsl:if>
				<xsl:if test="/*/pkg:changelog">
					<changelog>
						<xsl:copy-of select="/*/pkg:changelog/node()" />
					</changelog>
				</xsl:if>
				<xsl:if test="/*/pkg:deployed">
					<deployed>
						<xsl:value-of select="/*/pkg:deployed" />
					</deployed>
				</xsl:if>
			</meta>
		</xsl:result-document>
	</xsl:template>

</xsl:stylesheet>