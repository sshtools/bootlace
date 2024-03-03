package com.sshtools.bootlace.api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class XML {
	
	public final static XML of(InputStream in) {
		try {
			var docBuilderFactory = DocumentBuilderFactory.newInstance();
			var docBuilder = docBuilderFactory.newDocumentBuilder();
			return new XML(docBuilder.parse(in));
		}
		catch(RuntimeException re) {
			throw re;
		}
		catch(Exception e) {
			throw new IllegalArgumentException("Failed to parse XML.", e);
		}
	}

	private final Node node;

	private XML(Node document) {
		this.node = document;
	}
	
	/*
	 * for (int i = 0; i < els.getLength(); i++) {
				var varEl = els.item(i);
				if (varEl instanceof Element) {
					var el = (Element) varEl;
					var name = el.getAttribute("name");
					if (name.equals("sys.version")) {
						detectedVersion = el.getAttribute("value");
						break;
					}
				}
			}
	 */
	
	
	public Optional<XML> child(String tagName) {
		var itms = byTagName(tagName);
		if (itms.getLength() == 0) {
			return Optional.empty();
		} else {
			return Optional.of(new XML(itms.item(0)));
		}
	}
	
	public List<XML> children() {
		var l = new ArrayList<XML>();
		var chs = element().getChildNodes();
		for(int i = 0 ; i < chs.getLength(); i++) {
			var item = chs.item(i);
			if(item instanceof Element el) {
				l.add(new XML(el));
			}
		}
		return l;
	}

	public Optional<String> value(String tagName) {
		var itms = byTagName(tagName);
		if (itms.getLength() == 0) {
			return Optional.empty();
		} else {
			return Optional.ofNullable(((Element) itms.item(0)).getTextContent());
		}
	}
	
	private Element element() {
		if(node instanceof Document doc) {
			return doc.getDocumentElement();
		}
		else if(node instanceof Element el) {
			return el;
		}
		else
			throw new IllegalStateException();		
	}

	private NodeList byTagName(String tagName) {
		return element().getElementsByTagName(tagName);
	}

	public void dumpEl() {
		var el = element();
		System.out.println("---- " + el);
		var chs = el.getChildNodes();
		for(int i = 0 ; i < chs.getLength(); i++) {
			var item = chs.item(i);
			System.out.println(item);
		}
		System.out.println("----");
	}
}
