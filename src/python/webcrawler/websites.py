# -*- coding: utf-8 -*-

from webbox import WebBox
from parser import Parser


class DealMoonBox(WebBox):
    """Class to process dealmoon.cn
    """
    def __init__(self, start, finish, url, page, cCSS, nCSS, cPath, nPath):
        WebBox.__init__(self, start, finish, url, page, cCSS, nCSS, cPath, nPath)
        self.rootUrl = 'http://dealmoon.cn'

    def unpack(self):
        results = WebBox.unpack(self)
        if results:
            if isinstance(results, tuple):
                urls = results[1]
                urls = [self.rootUrl+url for url in urls]
                return results[0], urls
            else:
                return [self.rootUrl+url for url in urls]
        else:
            return False

    def getRootUrl(self):
        return self.rootUrl

    def setRootUrl(self, url):
        self.rootUrl = url

    def pack(self):
        return [DealMoonBox(0, 1, url, None, self.cCSS, self.nCSS, self.cPath, self.nPath)
                for url in self.urls]

    def getCategory(self):
        liCSS = ['div.navBot li.active1']
        catCSS = ['a']

        p = Parser(liCSS)
        catLists = p.parseDom(self.page)
        p = Parser(catCSS)
        return [p.parseContent(catList)
                for catList in catLists]

    def getId(self):
        p = Parser(['div.mlist'])
        cList = p.parseDom(self.page)
        return map(lambda x: x.attrib['data-id'], cList)
