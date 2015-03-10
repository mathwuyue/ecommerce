# -*- coding: utf-8 -*-

import lxml.html
import HTMLParser
from lxml.cssselect import CSSSelector


class Parser(object):
    """
    Parser class for parsing the pages that are grabbed.
    """

    def __init__(self, cCSS=[], nCSS=[], cPath=[], nPath=[], encoding='utf-8'):
        """
        cCSS: CSS rules to get the contents.
        cRules: Other rules to get the contents.
        nRules: Rules to get the "next" url.
        sRules:
        """
        if cCSS:
            self.cCSSSel = [CSSSelector(c) for c in cCSS]
        else:
            self.nCSSSel = []
        if nCSS:
            self.nCSSSel = [CSSSelector(n) for n in nCSS]
        else:
            self.nCSSSel = []
        self.cPath = cPath
        self.nPath = nPath
        self.parser = lxml.html.HTMLParser(encoding=encoding)
        self.htmlParser = HTMLParser.HTMLParser()

    def parseDom(self, content):
        if isinstance(content, str):
            content = lxml.html.fromstring(content, parser=self.parser)
        self.cDOMs = [sel(content) for sel in self.cCSSSel]
        self.cDOMs = self._getList(self.cDOMs)
        # return contents
        return self.cDOMs

    def parseContent(self, page):
        if isinstance(page, str):
            page = lxml.html.fromstring(page, parser=self.parser)
        # get all doms
        self.cDOMs = [sel(page) for sel in self.cCSSSel]
        self.cDOMs = self._getList(self.cDOMs)
        # return contents
        return [self.htmlParser.unescape(lxml.html.tostring(c))
                for c in self.cDOMs]

    def getNextUrl(self, page):
        page = lxml.html.fromstring(page, parser=self.parser)
        # get next doms
        self.nDOMs = [sel(page) for sel in self.nCSSSel]
        # 2 -> 1
        self.nDOMs = self._getList(self.nDOMs)
        # return list of next URLs
        return [n.attrib['href'] for n in self.nDOMs]

    def isStop(self, cContent, pContent):
        pass

    def _getList(self, matrix):
        # two dimensions -> 1 dimension list
        return reduce(lambda x, y: x+y, matrix)
