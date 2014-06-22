package appeng.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.ContainerNull;
import appeng.me.cache.CraftingCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import cpw.mods.fml.common.FMLCommonHandler;

public class CraftingTreeProcess
{

	World world;
	CraftingTreeNode parent;
	ICraftingPatternDetails details;
	CraftingJob job;

	long crafts = 0;
	boolean damageable;
	boolean fullsimulation;

	final private int depth;

	Map<CraftingTreeNode, Long> nodes = new HashMap();
	public boolean possible = true;

	public CraftingTreeProcess(CraftingCache cc, CraftingJob job, ICraftingPatternDetails details, CraftingTreeNode craftingTreeNode, int depth, World world) {
		parent = craftingTreeNode;
		this.details = details;
		this.job = job;
		this.depth = depth;
		world = job.getWorld();

		if ( details.isCraftable() )
		{
			IAEItemStack list[] = details.getInputs();

			InventoryCrafting ic = new InventoryCrafting( new ContainerNull(), 3, 3 );
			IAEItemStack[] is = details.getInputs();
			for (int x = 0; x < ic.getSizeInventory(); x++)
				ic.setInventorySlotContents( x, is[x] == null ? null : is[x].getItemStack() );

			FMLCommonHandler.instance().firePlayerCraftingEvent( Platform.getPlayer( (WorldServer) world ), details.getOutput( ic, world ), ic );

			for (int x = 0; x < ic.getSizeInventory(); x++)
			{
				ItemStack g = ic.getStackInSlot( x );
				if ( g != null && g.stackSize > 1 )
					fullsimulation = true;
			}

			for (int x = 0; x < list.length; x++)
			{
				IAEItemStack part = list[x];
				if ( part != null )
				{
					ItemStack g = part.getItemStack();

					if ( g.getItem().hasContainerItem( g ) )
						damageable = true;

					nodes.put( new CraftingTreeNode( cc, job, part.copy(), this, x, depth + 1 ), part.getStackSize() );
				}
			}
		}
		else
		{
			for (IAEItemStack part : details.getCondencedInputs())
			{
				nodes.put( new CraftingTreeNode( cc, job, part.copy(), this, -1, depth + 1 ), part.getStackSize() );
			}
		}
	}

	public boolean notRecurive(ICraftingPatternDetails details)
	{
		return parent.notRecurive( details );
	}

	long getTimes(long remaining, long stackSize)
	{
		if ( damageable || fullsimulation )
			return 1;
		return (remaining / stackSize) + (remaining % stackSize != 0 ? 1 : 0);
	}

	IAEItemStack getAmountCrafted(IAEItemStack what2)
	{
		for (IAEItemStack is : details.getCondencedOutputs())
		{
			if ( is.equals( what2 ) )
			{
				what2 = what2.copy();
				what2.setStackSize( is.getStackSize() );
				return what2;
			}
		}

		throw new RuntimeException( "Crafting Tree construction failed." );
	}

	public void request(MECraftingInventory inv, long i, BaseActionSource src) throws CraftBranchFailure, InterruptedException
	{
		if ( Thread.interrupted() )
			throw new InterruptedException();

		if ( fullsimulation )
		{
			InventoryCrafting ic = new InventoryCrafting( new ContainerNull(), 3, 3 );

			for (Entry<CraftingTreeNode, Long> entry : nodes.entrySet())
			{
				IAEItemStack item = entry.getKey().getStack( entry.getValue() );
				IAEItemStack stack = entry.getKey().request( inv, item.getStackSize(), src );

				ic.setInventorySlotContents( entry.getKey().slot, stack.getItemStack() );
			}

			FMLCommonHandler.instance().firePlayerCraftingEvent( Platform.getPlayer( (WorldServer) world ), details.getOutput( ic, world ), ic );

			for (int x = 0; x < ic.getSizeInventory(); x++)
			{
				ItemStack is = ic.getStackInSlot( x );
				is = Platform.getContainerItem( is );

				IAEItemStack o = AEApi.instance().storage().createItemStack( is );
				if ( o != null )
					inv.injectItems( o, Actionable.MODULATE, src );
			}
		}
		else
		{
			// request and remove inputs...
			for (Entry<CraftingTreeNode, Long> entry : nodes.entrySet())
			{
				IAEItemStack item = entry.getKey().getStack( entry.getValue() );
				IAEItemStack stack = entry.getKey().request( inv, item.getStackSize() * i, src );

				if ( damageable )
				{
					ItemStack is = Platform.getContainerItem( stack.getItemStack() );
					IAEItemStack o = AEApi.instance().storage().createItemStack( is );
					if ( o != null )
						inv.injectItems( o, Actionable.MODULATE, src );
				}
			}
		}

		// assume its possible.

		// add crafting results..
		for (IAEItemStack out : details.getCondencedOutputs())
		{
			IAEItemStack o = out.copy();
			o.setStackSize( o.getStackSize() * i );
			inv.injectItems( o, Actionable.MODULATE, src );
		}

		crafts += i;
	}

	public void dive(CraftingJob job)
	{
		job.addTask( getAmountCrafted( parent.getStack( 1 ) ), crafts, details, depth );
		for (CraftingTreeNode pro : nodes.keySet())
			pro.dive( job );
	}

	public void setSimulate()
	{
		crafts = 0;

		for (CraftingTreeNode pro : nodes.keySet())
			pro.setSimulate();
	}

	public void setJob(MECraftingInventory storage, CraftingCPUCluster craftingCPUCluster, BaseActionSource src) throws CraftBranchFailure
	{
		craftingCPUCluster.addCrafting( details, crafts );

		for (CraftingTreeNode pro : nodes.keySet())
			pro.setJob( storage, craftingCPUCluster, src );
	}
}
