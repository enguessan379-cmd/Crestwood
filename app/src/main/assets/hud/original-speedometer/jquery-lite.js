(function (global) {
  "use strict";

  function Query(nodes) {
    this.nodes = nodes || [];
  }

  Query.prototype.ready = function (callback) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", callback, { once: true });
    } else {
      callback();
    }
    return this;
  };

  Query.prototype.css = function (name, value) {
    if (value === undefined) {
      var node = this.nodes[0];
      return node ? global.getComputedStyle(node).getPropertyValue(name) : undefined;
    }
    this.nodes.forEach(function (node) {
      node.style.setProperty(name, String(value));
    });
    return this;
  };

  Query.prototype.addClass = function (name) {
    this.nodes.forEach(function (node) { node.classList.add(name); });
    return this;
  };

  Query.prototype.removeClass = function (name) {
    this.nodes.forEach(function (node) { node.classList.remove(name); });
    return this;
  };

  Query.prototype.html = function (value) {
    this.nodes.forEach(function (node) { node.innerHTML = value; });
    return this;
  };

  Query.prototype.toArray = function () {
    return this.nodes.slice();
  };

  Query.prototype.fadeIn = function () {
    this.nodes.forEach(function (node) {
      node.style.opacity = "1";
      node.style.display = "block";
    });
    return this;
  };

  Query.prototype.fadeOut = function () {
    this.nodes.forEach(function (node) {
      node.style.opacity = "0";
      node.style.display = "none";
    });
    return this;
  };

  function $(value) {
    if (value === document || value === global || value instanceof Element) {
      return new Query([value]);
    }
    if (typeof value === "string") {
      return new Query(Array.prototype.slice.call(document.querySelectorAll(value)));
    }
    if (value && typeof value.length === "number") {
      return new Query(Array.prototype.slice.call(value));
    }
    return new Query([]);
  }

  global.$ = global.jQuery = $;
})(window);
