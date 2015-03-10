# -*- coding: utf-8 -*-

import gevent.monkey
gevent.monkey.patch_socket()

import gevent
from gevent.queue import Queue, Empty
from gevent.pool import Pool
from gevent.lock import BoundedSemaphore
import urllib2
import codecs
import string
import lxml.html
from lxml.html.clean import clean_html, Cleaner
import os
import logging
import HTMLParser

from websites import DealMoonBox


class Engine(object):
    """ General Engine class
    """

    def __init__(self, configFile, logFile, headers):
        self.configFile = configFile
        self.headers = headers
        # log file
        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(logging.DEBUG)
        # self.logger.propagate = False
        fh = logging.FileHandler(logFile)
        fh.setLevel(logging.INFO)
        formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s', '%m/%d/%Y %I:%M:%S %p')
        fh.setFormatter(formatter)
        self.logger.addHandler(fh)

    def load(self):
        """ Read the config. and load all webboxs and init URL.
        """
        self.dFinish = 1
        self.dcCSS = ['div.mlist h2 a', 'div.mlist div.mbody', 'div.mlist div.mpic img']
        self.dnCSS = ['div.pagelink a.link']

    def run(self):
        logging.info('Crawler engine starts...')

    def stop(self):
        logging.info('Engine stop.')

    def checkQueue(self, q_type):
        """ Check queues status
        """
        pass


class GeventEngine(Engine):
    """ Worker utilise gevent.queue
    """

    def __init__(self, configFile, logFile, headers, catDict, urls, pool_size=100, n_writers=6):
        """ initialise gevent queue
            resultQ: queue for writing files
            crawlerQ: queue to get pages
        """
        Engine.__init__(self, configFile, logFile, headers)
        self.resultQ = Queue()
        self.urls = urls
        self.catDict = catDict
        self.hp = HTMLParser.HTMLParser()
        self.pool = Pool(pool_size)
        self.n_tasks = len(self.urls)
        self.n_finish = 0
        self.n_writers = n_writers
        self.sem = BoundedSemaphore()

    def run(self):
        Engine.run(self)
        self.pool.map(self._download, self.urls)
        gevent.joinall([gevent.spawn(self._handle)
                        for i in xrange(self.n_writers)])
        self.logger.info("Crawlers finish successfully")

    def stop(self):
        Engine.stop(self)
        self.pool.kill()

    def _download(self, url):
        self.logger.debug("Download page: %s", url)
        try:
            page = urllib2.urlopen(url).read()
            self.logger.debug("%s downloaded successfully", url)
            self.resultQ.put(DealMoonBox(0, self.dFinish, url, page, [self.dcCSS,], [self.dnCSS,], [[],], [[],]))
            self.logger.debug("Put %s into resultQ", url)
        except:
            self.logger.error("HTTP errors %s", url)

    def _downloadImage(self, item):
        dom = item[0]
        idxImg = item[1]
        suffix = item[2]
        self.logger.debug("%s-%s: Picture downloading...", suffix, idxImg)
        dom = lxml.html.fromstring(dom)
        try:
            url = dom.attrib['src']
        except KeyError:
            try:
                url = dom.attrib['data-original']
            except KeyError:
                self.logger.error(dom.tostring(), exc_info=True)
                return -1
        if 'dealmoon' not in url:
            url = 'http://img.dealmoon.com' + url
        f_img = open('img/%s/%s.jpg'%(suffix, idxImg), 'wb')
        f_img.write(urllib2.urlopen(url).read())
        f_img.close()
        self.logger.debug("%s-%s: Finish.", suffix, idxImg)

    def _clean_title(self, title):
        h_title = lxml.html.fromstring(title)
        cleaner = Cleaner(allow_tags=[''], remove_unknown_tags=False)
        cleaner(h_title)
        return ' '.join(h_title.text_content().split())

    def _clean_content(self, content):
        content = content.split('<!--')[0]
        h_content = lxml.html.fromstring(content)
        i = 0
        all_links = map(lambda l: l.attrib['href'], h_content.cssselect('a'))
        clean_links = self.pool.map(self._clean_url, all_links)
        buy_links = []
        for e, a, l, p in h_content.iterlinks():
            if a == 'href':
                e.set('href', clean_links[i])
                # test whether is buy now links
                if '点击购买'.decode('utf-8') in e.text_content():
                    buy_links.append(clean_links[i])
                i = i+1
            if 'onclick' in e.attrib:
                del(e.attrib['onclick'])
            if 'trk' in e.attrib:
                del(e.attrib['trk'])
        # return results and buy_links
        return self.hp.unescape(lxml.html.tostring(h_content)), buy_links

    def _clean_url(self, el):
        if 'exec' not in el:
            return '#'
        if not el.startswith('http'):
            tmpUrl = 'http://cn.dealmoon.com' + el
        else:
            tmpUrl = el
        try:
            tmpReq = urllib2.Request(tmpUrl, headers=self.headers)
            tmpC = urllib2.urlopen(tmpReq, timeout=5).read()
        except Exception as e:
            self.logger.error('Cannot track intermediate URL: %s\nMessage: %s', tmpUrl, str(e))
            return urllib2.unquote(tmpUrl).decode('utf-8')
        tmpH = lxml.html.fromstring(tmpC)
        tmp = tmpH.cssselect('meta')[0].attrib['content']
        # fixme, not final one
        cleanUrl = tmp.split(';url=')[1]
        tmpIdx = string.find(cleanUrl, 'http', 4)
        if tmpIdx > -1:
            cleanUrl = cleanUrl[tmpIdx:]
        else:
            try:
                tmpReq = urllib2.Request(cleanUrl, headers=self.headers)
                cleanUrl = urllib2.urlopen(tmpReq, timeout=5).geturl()
            except Exception as e:
                self.logger.debug('Cannot track final URL: %s\nMessage: %s', cleanUrl, str(e))
        return urllib2.unquote(cleanUrl).decode('utf-8')

    def _handle(self):
        while True:
            try:
                dBox = self.resultQ.get_nowait()
                # get one task done
                self.sem.acquire()
                self.n_finish = self.n_finish + 1
                self.sem.release()
                # process result
                results = dBox.unpack()
                ids = dBox.getId()
                url = dBox.getUrl()
                n_items = len(results[0]) / 3
                if n_items*3 != len(results[0]):
                    self.logger.error('Crawler error.')
                    return -1
                # write to the file
                result = results[0]
                self.logger.debug('%s: write results.', url)
                if 'sort=time' in url:
                    suffix = 'pages'
                    pageIdx = url.split('&')[2].split('=')[1]
                    # log pages list order
                    with open('%s.log'%pageIdx, 'w') as f:
                        f.write(string.join(ids, '\n'))
                else:
                    suffix = 'cat/%d'%self.catDict[url]
                for i in range(n_items):
                    with codecs.open('%s/%s'%(suffix, ids[i]), 'w', encoding='utf-8') as f:
                        # title
                        title = self._clean_title(result[i])
                        f.write(title)
                        f.write('\n###\n')
                        # content and buy now links
                        self.logger.debug("%s:%s track URLs..."%(suffix, ids[i]))
                        c, bls = self._clean_content(result[n_items+i])
                        f.write(c)
                        f.write('\n###\n')
                        f.write(' '.join(bls))
                # get images: third part of result
                i = 2
                # mkdir to store pics
                if not os.path.exists('img/%s' % suffix):
                    os.makedirs('img/%s' % suffix)
                # get pictures and write them to files
                self.logger.debug('%s: write pictures', url)
                doms = result[i*n_items:]
                items = [[doms[j], ids[j], suffix] for j in range(n_items)]
                self.pool.map(self._downloadImage, items)
                self.logger.debug('%s: Finish.', suffix)
            except Empty:
                self.sem.acquire()
                n_finish = self.n_finish
                self.sem.release()
                if n_finish < self.n_tasks:
                    gevent.sleep(1)
                else:
                    self.logger.info("All tasks finish")
                    return 0
