/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.bootlace.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.sshtools.bootlace.api.DirectedGraph.SCC;

/**
 * Maintains the build dependencies between {@link NodeModel}s for efficient
 * dependency computation.
 *
 * <p>
 * The "master" data of dependencies are owned/persisted/maintained by
 * individual {@link NodeModel}s, but because of that, it's relatively slow to
 * compute backward edges.
 *
 * <p>
 * This class builds the complete bi-directional dependency graph by collecting
 * information from all {@link NodeModel}s.
 *
 * <p>
 * Once built, {@link DependencyGraph} is immutable, and every time there's a
 * change (which is relatively rare), a new instance will be created. This
 * eliminates the need of synchronization.
 *
 * @see Jenkins#getDependencyGraph()
 * @author Kohsuke Kawaguchi
 */
public class DependencyGraph<M extends NodeModel<M>> implements Comparator<M> {

	private Map<M, List<DependencyGroup<M>>> forward = new HashMap<>();
	private Map<M, List<DependencyGroup<M>>> backward = new HashMap<>();

	private transient Map<Class<?>, Object> computationalData;

	private Comparator<M> topologicalOrder;
	private List<M> topologicallySorted;
	
	
	@SuppressWarnings("unchecked")
	public DependencyGraph(M... nodes) {
		this(Arrays.asList(nodes));
	}
	
	public DependencyGraph(Collection<M> nodes) {
		this.computationalData = new HashMap<>();
		for (var node : nodes) {
			node.dependencies(dep -> {
				add(forward, dep.getUpstream(), dep);
				add(backward, dep.getDownstream(), dep);		
			});
		}

		forward = finalize(forward);
		backward = finalize(backward);
		topologicalDagSort();
		this.computationalData = null;
	}

	/**
	 *
	 *
	 * See <a href=
	 * "https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm">Tarjan's
	 * strongly connected components algorithm</a>
	 */
	private void topologicalDagSort() {
		DirectedGraph<M> g = new DirectedGraph<>() {
			@Override
			protected Collection<M> nodes() {
				final Set<M> nodes = new HashSet<>();
				nodes.addAll(forward.keySet());
				nodes.addAll(backward.keySet());
				return nodes;
			}

			@Override
			protected Collection<M> forward(M node) {
				return getDownstream(node);
			}
		};

		List<SCC<M>> sccs = g.getStronglyConnectedComponents();

		final Map<M, Integer> topoOrder = new HashMap<>();
		topologicallySorted = new ArrayList<>();
		int idx = 0;
		for (SCC<M> scc : sccs) {
			for (M n : scc) {
				topoOrder.put(n, idx++);
				topologicallySorted.add(n);
			}
		}

		topologicalOrder = Comparator.comparingInt(topoOrder::get);

		topologicallySorted = Collections.unmodifiableList(topologicallySorted);
	}

	/**
	 * Adds data which is useful for the time when the dependency graph is built up.
	 * All this data will be cleaned once the dependency graph creation has
	 * finished.
	 */
	public <T> void putComputationalData(Class<T> key, T value) {
		this.computationalData.put(key, value);
	}

	/**
	 * Gets temporary data which is needed for building up the dependency graph.
	 */
	public <T> T getComputationalData(Class<T> key) {
		@SuppressWarnings("unchecked")
		T result = (T) this.computationalData.get(key);
		return result;
	}

	/**
	 * Gets all the immediate downstream node models (IOW forward edges) of the given
	 * node model.
	 *
	 * @return can be empty but never null.
	 */
	public List<M> getDownstream(M p) {
		return get(forward, p, false);
	}

	/**
	 * Gets all the immediate upstream node models (IOW backward edges) of the given
	 * node model.
	 *
	 * @return can be empty but never null.
	 */
	public List<M> getUpstream(M p) {
		return get(backward, p, true);
	}

	private List<M> get(Map<M, List<DependencyGroup<M>>> map, M src, boolean up) {
		List<DependencyGroup<M>> v = map.get(src);
		if (v == null)
			return Collections.emptyList();
		List<M> result = new ArrayList<>(v.size());
		for (DependencyGroup<M> d : v)
			result.add(up ? d.getUpstream() : d.getDownstream());
		return result;
	}

	/**
	 * @since 1.341
	 */
	public List<Dependency<M>> getDownstreamDependencies(M p) {
		return get(forward, p);
	}

	/**
	 * @since 1.341
	 */
	public List<Dependency<M>> getUpstreamDependencies(M p) {
		return get(backward, p);
	}

	private List<Dependency<M>> get(Map<M, List<DependencyGroup<M>>> map, M src) {
		List<DependencyGroup<M>> v = map.get(src);
		if (v == null) {
			return Collections.emptyList();
		} else {
			List<Dependency<M>> builder = new ArrayList<>();
			for (DependencyGroup<M> dependencyGroup : v) {
				builder.addAll(dependencyGroup.getGroup());
			}
			return Collections.unmodifiableList(builder);
		}

	}

	/**
	 * Returns true if a node model has a non-direct dependency to another node model.
	 * <p>
	 * A non-direct dependency is a path of dependency "edge"s from the source to
	 * the destination, where the length is greater than 1.
	 */
	public boolean hasIndirectDependencies(M src, M dst) {
		Set<M> visited = new HashSet<>();
		Stack<M> queue = new Stack<>();

		queue.addAll(getDownstream(src));
		queue.remove(dst);

		while (!queue.isEmpty()) {
			M p = queue.pop();
			if (p == dst)
				return true;
			if (visited.add(p))
				queue.addAll(getDownstream(p));
		}

		return false;
	}

	/**
	 * Gets all the direct and indirect upstream dependencies of the given node model.
	 */
	public Set<M> getTransitiveUpstream(M src) {
		return getTransitive(backward, src, true);
	}

	/**
	 * Gets all the direct and indirect downstream dependencies of the given
	 * node model.
	 */
	public Set<M> getTransitiveDownstream(M src) {
		return getTransitive(forward, src, false);
	}

	private Set<M> getTransitive(Map<M, List<DependencyGroup<M>>> direction, M src, boolean up) {
		Set<M> visited = new HashSet<>();
		Stack<M> queue = new Stack<>();

		queue.add(src);

		while (!queue.isEmpty()) {
			M p = queue.pop();

			for (M child : get(direction, p, up)) {
				if (visited.add(child))
					queue.add(child);
			}
		}

		return visited;
	}

	@SuppressWarnings("unused")
	private void add(Map<M, List<DependencyGroup<M>>> map, M key, Dependency<M> dep) {
		List<DependencyGroup<M>> set = map.computeIfAbsent(key, k -> new ArrayList<>());
		for (DependencyGroup<M> d : set) {
			// Check for existing edge that connects the same two node models:
			if (d.getUpstream() == dep.getUpstream()
					&& d.getDownstream() == dep.getDownstream()) {
				d.add(dep);
				return;
			}
		}
		// Otherwise add to list:
		set.add(new DependencyGroup<>(dep));
	}

	private Map<M, List<DependencyGroup<M>>> finalize(Map<M, List<DependencyGroup<M>>> m) {
		for (Map.Entry<M, List<DependencyGroup<M>>> e : m.entrySet()) {
			e.getValue().sort(NAME_COMPARATOR);
			e.setValue(Collections.unmodifiableList(e.getValue()));
		}
		return Collections.unmodifiableMap(m);
	}

	private static final Comparator<DependencyGroup<? extends NodeModel<?>>> NAME_COMPARATOR = new Comparator<>() {
		@Override
		public int compare(DependencyGroup<? extends NodeModel<?>> lhs, DependencyGroup<? extends NodeModel<?>> rhs) {
			int cmp = lhs.getUpstream().name().compareTo(rhs.getUpstream().name());
			return cmp != 0 ? cmp
					: lhs.getDownstream().name().compareTo(rhs.getDownstream().name());
		}
	};

	/**
	 * Compare two node models based on the topological order defined by this
	 * Dependency Graph
	 */
	@Override
	public int compare(M o1, M o2) {
		return topologicalOrder.compare(o1, o2);
	}

	/**
	 * Returns all the node models in the topological order of the dependency.
	 *
	 * Intuitively speaking, the first one in the list is the source of the
	 * dependency graph, and the last one is the sink.
	 *
	 * @since 1.521
	 */
	public List<M> getTopologicallySorted() {
		return topologicallySorted;
	}

	/**
	 * Represents an edge in the dependency graph.
	 * 
	 * @since 1.341
	 */
	public static class Dependency<M> {
		private M upstream, downstream;

		public Dependency(M upstream, M downstream) {
			this.upstream = upstream;
			this.downstream = downstream;
		}

		public M getUpstream() {
			return upstream;
		}

		public M getDownstream() {
			return downstream;
		}

		/**
		 * Does this method point to itself?
		 */
		public boolean pointsItself() {
			return upstream == downstream;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object obj) {
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;

			final Dependency<M> that = (Dependency<M>) obj;
			return this.upstream == that.upstream || this.downstream == that.downstream;
		}

		@Override
		public int hashCode() {
			int hash = 7;
			hash = 23 * hash + this.upstream.hashCode();
			hash = 23 * hash + this.downstream.hashCode();
			return hash;
		}

		@Override
		public String toString() {
			return super.toString() + "[" + upstream + "->" + downstream + "]";
		}
	}

	/**
	 * Collect multiple dependencies between the same two node models.
	 */
	private static class DependencyGroup<M> {
		private Set<Dependency<M>> group = new LinkedHashSet<>();

		DependencyGroup(Dependency<M> first) {
			this.upstream = first.getUpstream();
			this.downstream = first.getDownstream();
			group.add(first);
		}

		private void add(Dependency<M> next) {
			group.add(next);
		}

		public Set<Dependency<M>> getGroup() {
			return group;
		}

		private M upstream, downstream;

		public M getUpstream() {
			return upstream;
		}

		public M getDownstream() {
			return downstream;
		}
	}
}
