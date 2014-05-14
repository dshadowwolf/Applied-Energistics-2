package appeng.me.cluster.implementations;

import java.util.Iterator;
import java.util.LinkedList;

import appeng.api.networking.IGridHost;
import appeng.api.util.WorldCoord;
import appeng.me.cluster.IAECluster;
import appeng.tile.crafting.TileCraftingTile;

public class CraftingCPUCluster implements IAECluster
{

	public WorldCoord min;
	public WorldCoord max;
	public boolean isDestroyed = false;

	private LinkedList<TileCraftingTile> tiles = new LinkedList();

	int accelerator = 0;
	private LinkedList<TileCraftingTile> storage = new LinkedList<TileCraftingTile>();
	private LinkedList<TileCraftingTile> status = new LinkedList<TileCraftingTile>();

	@Override
	public Iterator<IGridHost> getTiles()
	{
		return (Iterator) tiles.iterator();
	}

	public CraftingCPUCluster(WorldCoord _min, WorldCoord _max) {
		min = _min;
		max = _max;
	}

	@Override
	public void updateStatus(boolean updateGrid)
	{
		for (TileCraftingTile r : tiles)
		{
			r.updateMeta();
		}
	}

	@Override
	public void destroy()
	{
		if ( isDestroyed )
			return;
		isDestroyed = true;

		for (TileCraftingTile r : tiles)
		{
			r.updateStatus( null );
		}
	}

	public void addTile(TileCraftingTile te)
	{
		tiles.add( te );

		if ( te.isStorage() )
			storage.add( te );
		else if ( te.isStatus() )
			status.add( te );
		else if ( te.isAccelerator() )
			accelerator++;
	}

}