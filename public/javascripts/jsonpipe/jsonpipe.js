/**
 * Based on https://github.com/eBay/jsonpipe
 * Sep 24, 2015
 */
'use strict';

var jsonpipe = {};
jsonpipe.isString = function(str) {
    return Object.prototype.toString.call(str) === '[object String]';
};
jsonpipe.isFunction = function(fn) {
    return Object.prototype.toString.call(fn) === '[object Function]';
};
// Do the eval trick, since JSON object not present
jsonpipe.customParse = function(chunk) {
    if (!chunk || !/^[\{|\[].*[\}|\]]$/.test(chunk)) {
        throw new Error('parseerror');
    }
    return eval('(' + chunk + ')');
};
jsonpipe.parse = function(chunk, success, error) {
    var isFunction = jsonpipe.isFunction;
    var customParse = jsonpipe.customParse;
    var jsonObj;
    try {
        if (typeof JSON !== 'undefined') {
            jsonObj = {};
            var splitChunk = chunk.split('\n');
            if (splitChunk[0].startsWith('event: ')) {
                jsonObj['event'] = splitChunk.shift().substr(7);
            }
            if (splitChunk[0].startsWith('id: ')) {
                jsonObj['id'] = splitChunk.shift().substr(4);
            }
            jsonObj['data'] = JSON.parse(splitChunk[0].substr(6));
        } else {
            jsonObj = customParse(chunk);
        }
    } catch (ex) {
        if (isFunction(error)) {
            error('parsererror');
        }
        return;
    }
    // No parse error proceed to success
    if (jsonObj && isFunction(success)) {
        success(jsonObj);
    }
};
/**
 * @param {String} url A string containing the URL to which the request is sent.
 * @param {Object} url A set of key/value pairs that configure the Ajax request.
 * @return {XMLHttpRequest} The XMLHttpRequest object for this request.
 * @method ajax
 */
jsonpipe.flow = function(url, options) {
    // Do all prerequisite checks
    if (!url) {
        return;
    }

    // Set arguments if first argument is not string
    if (!jsonpipe.isString(url)) {
        options = url;
        url = options.url;
    }

    // Check if all mandatory attributes are present
    if (!url ||
        !options ||
        !(options.success || options.error || options.complete)) {
        return;
    }

    var parse = jsonpipe.parse;
    var offset = 0,
        token = options.delimiter || '\n\n',
        onChunk = function(text, finalChunk) {
            var chunk = text.substring(offset),
                start = 0,
                finish = chunk.indexOf(token, start),
                successFn = options.success,
                errorFn = options.error,
                subChunk;

            if (finish === 0) { // The delimiter is at the beginning so move the start
                start = finish + token.length;
            }

            while ((finish = chunk.indexOf(token, start)) > -1) {
                subChunk = chunk.substring(start, finish);
                if (subChunk) {
                    parse(subChunk, successFn, errorFn);
                }
                start = finish + token.length; // move the start
            }
            offset += start; // move the offset

            // Get the remaning chunk
            chunk = text.substring(offset);
            // If final chunk and still unprocessed chunk and no delimiter, then execute the full chunk
            if (finalChunk && chunk && finish === -1) {
                parse(chunk, successFn, errorFn);
            }
        };

    // Assign onChunk to options
    options.onChunk = onChunk;

    return jsonpipe.send(url, options);
};

jsonpipe.send = function(url, options) {
    if (!url || !options) {
        return;
    }

    var xhr = new XMLHttpRequest(),
        state = {
            UNSENT: 0,
            OPENED: 1,
            HEADERS_RECEIVED: 2,
            LOADING: 3,
            DONE: 4
        },
        noop = function() {},
        method = (options.method || '').toUpperCase(),
        headers = options.headers,
        onChunk = options.onChunk || noop,
        errorFn = options.error || noop,
        completeFn = options.complete || noop,
        addContentHeader = method === 'POST',
        isChunked = false,
        timer;

    xhr.open(method || 'GET', url, true);

    // Attach onreadystatechange
    xhr.onreadystatechange = function() {
        var encoding,
            chromeObj,
            loadTimes,
            chromeSpdy;
        if (xhr.readyState === state.HEADERS_RECEIVED) {
            encoding = xhr.getResponseHeader('Transfer-Encoding') || '';
            encoding = encoding.toLowerCase();
            isChunked = encoding.indexOf('chunked') > -1 ||
                encoding.indexOf('identity') > -1; // fix for Safari
            if (!isChunked) {
                // SPDY inherently uses chunked transfer and does not define a header.
                // Firefox provides a synthetic header which can be used instead.
                // For Chrome, a non-standard JS function must be used to determine if
                // the primary document was loaded with SPDY.  If the primary document
                // was loaded with SPDY, then most likely the XHR will be as well.
                chromeObj = window.chrome;
                loadTimes = chromeObj && chromeObj.loadTimes && chromeObj.loadTimes();
                chromeSpdy = loadTimes && loadTimes.wasFetchedViaSpdy;
                isChunked = !!(xhr.getResponseHeader('X-Firefox-Spdy') || chromeSpdy);
            }
        } else if (xhr.readyState === state.LOADING) {
            if (isChunked && xhr.responseText) {
                onChunk(xhr.responseText);
            }
        } else if (xhr.readyState === state.DONE) {
            // clear timeout first
            clearTimeout(timer);
            // Check for error first
            if (xhr.status !== 200) {
                errorFn(xhr.statusText);
            } else {
                onChunk(xhr.responseText, true);
            }
            // Call complete at the end
            completeFn(xhr.statusText);
        }
    };

    // Add headers
    if (headers) {
        for (var key in headers) {
            xhr.setRequestHeader(key, headers[key]);
            if (key.toLowerCase() === 'content-type') {
                addContentHeader = false;
            }
        }
    }
    if (addContentHeader) {
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
    }

    // Add timeout
    if (options.timeout) {
        timer = setTimeout(function() {
            xhr.abort();
            clearTimeout(timer);
        }, options.timeout);
    }

    // Set credentials to true
    xhr.withCredentials = true;

    xhr.send(options.data);

    return xhr;
};
