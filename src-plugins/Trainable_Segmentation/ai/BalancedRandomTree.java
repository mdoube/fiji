package ai;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;

import weka.core.Instance;
import weka.core.Instances;

public class BalancedRandomTree implements Runnable, Serializable
{
	/**
	 * Generated serial version UID
	 */
	private static final long serialVersionUID = 1L;
	/** original data */
	Instances data = null;
	/** indices of the samples in the bag for this tree */
	ArrayList<Integer> bagIndices = null;
	/** split function generator */
	Splitter splitter = null;
	/** root node */
	BaseNode rootNode = null;

	/**
	 * Build random tree  
	 * @param numOfFeatures
	 * @param data
	 * @param bagIndices
	 */
	public BalancedRandomTree(
			final Instances data,
			ArrayList<Integer> bagIndices,
			final Splitter splitter)
	{
		this.data = data;
		this.bagIndices = bagIndices;
		//		System.out.println("Indices in bag: ");
		//		for(int i=0; i<bagIndices.size(); i++)
		//			System.out.println("index " + i + ": " + bagIndices.get(i));
		this.splitter = splitter;
	}

	/**
	 * Build the random tree based on the data specified 
	 * in the constructor 
	 */
	public void run() 
	{
		//rootNode = new InteriorNode(data, bagIndices, 0, splitter);
		rootNode = createTree(data, bagIndices, 0, splitter);
	}

	/**
	 * Evaluate sample
	 * 
	 * @param instance sample to evaluate
	 * @return array of class probabilities
	 */
	public double[] evaluate(Instance instance)
	{
		if (null == rootNode)
			return null;
		return rootNode.eval(instance);
	}


	/**
	 * Basic node of the tree
	 *
	 */
	abstract class BaseNode implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public abstract double[] eval( Instance instance );
		public int getDepth()
		{
			return 0;
		}
	}

	/**
	 * Leaf node in the tree 
	 *
	 */
	class LeafNode extends BaseNode implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		double[] probability;

		@Override
		public double[] eval(Instance instance) 
		{		
			return probability;
		}

		public LeafNode(double[] probability)
		{
			this.probability = probability;
		}

		/**
		 * Create leaf node based on the current split data
		 *  
		 * @param data pointer to original data
		 * @param indices indices at this node
		 */
		public LeafNode(
				final Instances data, 
				ArrayList<Integer> indices)
		{
			this.probability = new double[ data.numClasses() ];
			for(final Integer it : indices)
			{
				this.probability[ (int) data.get( it.intValue() ).classValue()] ++;
			}
			// Divide by the number of elements
			for(int i=0; i<data.numClasses(); i++)
				this.probability[i] /= (double) indices.size();
		}

	}
	/**
	 * Interior node of the tree
	 *
	 */
	class InteriorNode extends BaseNode implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		BaseNode left;
		BaseNode right;
		final int depth;
		final SplitFunction splitFn;

		private InteriorNode(int depth, SplitFunction splitFn) 
		{
			this.depth = depth;
			this.splitFn = splitFn;
		}

		/**
		 * Construct interior node of the tree
		 * 
		 * @param data pointer to the original set of samples
		 * @param indices indices of the samples at this node
		 * @param depth current tree depth
		 */
/*		public InteriorNode(
				final Instances data,
				final ArrayList<Integer> indices,
				final int depth,
				final Splitter splitFnProducer)
		{
			this.splitFn = splitFnProducer.getSplitFunction(data, indices);


			this.depth = depth;

			// left and right new arrays
			final ArrayList<Integer> leftArray = new ArrayList<Integer>();
			final ArrayList<Integer> rightArray = new ArrayList<Integer>();

			// split data
			int totalLeft = 0;
			int totalRight = 0;
			for(final Integer it : indices)
			{
				if( splitFn.evaluate( data.get(it.intValue()) ) )
				{
					leftArray.add(it);
					totalLeft ++;					
				}
				else
				{
					rightArray.add(it);
					totalRight ++;
				}
			}
			//System.out.println("total left = " + totalLeft + ", total rigth = " + totalRight + ", depth = " + depth);					
			//indices.clear();
			if( totalLeft == 0 )
			{
				left = new LeafNode(data, rightArray);
			}
			else if ( totalRight == 0 )
			{
				left = new LeafNode(data, leftArray);
			}
			else
			{
				left = new InteriorNode(data, leftArray, depth+1, splitFnProducer);
				right = new InteriorNode(data, rightArray, depth+1, splitFnProducer);
			}				
		}*/


		/**
		 * Evaluate sample at this node
		 */
		public double[] eval(Instance instance) 
		{
			if( null != right)
			{
				if(this.splitFn.evaluate( instance ) )
				{
					return left.eval(instance);
				}
				else
					return right.eval(instance);
			}
			else // leaves are always left nodes 
				return left.eval(instance);				
		}


		/**
		 * Get node depth
		 */
		public int getDepth()
		{
			return this.depth;
		}
	}

	/**
	 * Create random tree
	 * 
	 * @param data
	 * @param indices
	 * @param depth
	 * @param splitFnProducer
	 * @return root node
	 */
	private InteriorNode createTree(
			final Instances data,
			final ArrayList<Integer> indices,
			final int depth,
			final Splitter splitFnProducer)
	{
		int maxDepth = depth;
		// Create root node
		InteriorNode root = new InteriorNode(depth, splitFnProducer.getSplitFunction(data, indices));
		// Create list of nodes to process and add the root to it
		final LinkedList<InteriorNode> remainingNodes = new LinkedList<InteriorNode>();
		remainingNodes.add(root);
		// Create list of indices to process (it must match all the time with the node list)
		final LinkedList<ArrayList<Integer>> remainingIndices = new LinkedList<ArrayList<Integer>>();
		remainingIndices.add(indices);
		// While there is still nodes to process
		while (!remainingNodes.isEmpty()) 
		{
			final InteriorNode nd = remainingNodes.removeLast();
			final ArrayList<Integer> currentIndices = remainingIndices.removeLast();
			// left and right new arrays
			final ArrayList<Integer> leftArray = new ArrayList<Integer>();
			final ArrayList<Integer> rightArray = new ArrayList<Integer>();

			// split data
			for(final Integer it : currentIndices)
			{
				if( nd.splitFn.evaluate( data.get(it.intValue()) ) )
				{
					leftArray.add(it);
				}
				else
				{
					rightArray.add(it);
				}
			}
			//System.out.println("total left = " + leftArray.size() + ", total right = " + rightArray.size() + ", depth = " + nd.depth);					
			if(nd.depth > maxDepth)
				maxDepth = nd.depth;

			if( leftArray.size() == 0 )
			{
				nd.left = new LeafNode(data, rightArray);
				//System.out.println("Created leaf with feature " + nd.splitFn.index);
			}
			else if ( rightArray.size() == 0 )
			{
				nd.left = new LeafNode(data, leftArray);
				//System.out.println("Created leaf with feature " + nd.splitFn.index);
			}
			else
			{
				nd.left = new InteriorNode(nd.depth+1, splitFnProducer.getSplitFunction(data, leftArray));
				remainingNodes.add((InteriorNode)nd.left);
				remainingIndices.add(leftArray);
				
				nd.right = new InteriorNode(nd.depth+1, splitFnProducer.getSplitFunction(data, rightArray));
				remainingNodes.add((InteriorNode)nd.right);
				remainingIndices.add(rightArray);
			}
		}
		
		//System.out.println("Max depth = " + maxDepth);
		return root;
	}

}