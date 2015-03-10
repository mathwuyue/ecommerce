# -*- coding: utf-8 -*-

from parser import Parser


class NoPageException(Exception): pass


class WebBox(object):
    """
    """

    def __init__(self, start, finish, url='', page=None, cCSS=[], nCSS=[], cPath=[],
                 nPath=[], encoding='utf-8'):
        """FIX: need to check finish and the length of cCSS...
        """
        self.steps = start
        self.page = page
        self.parsers = [Parser(cCSS[i], nCSS[i], cPath[i], nPath[i], encoding)
                        for i in range(finish)]
        self.finish = finish
        self.url = url

    def setPage(self, page):
        self.page = page

    def unpack(self):
        if self.page is None:
            raise NoPageException()

        if self.steps < self.finish:
            parser = self.parsers[self.steps]
            self.steps = self.steps + 1
            if self.steps < self.finish-1:
                # steps before the final step
                self.urls = parser.getNextUrl(self.page)
                return self.urls
            else:
                # final step
                self.urls = parser.getNextUrl(self.page)
                self.contents = parser.parseContent(self.page)
                return self.contents, self.urls
        else:
            return False

    def getStatus(self):
        return self.steps, self.finish

    def getUrl(self):
        return self.url

    def setUrl(self, url):
        self.url = url
