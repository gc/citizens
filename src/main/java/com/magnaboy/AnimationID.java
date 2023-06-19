package com.magnaboy;

public enum AnimationID {
	Flex(8917),
	Think(857),
	Yawn(2111),
	SlapHead(4275),
	HumanIdle(808),
	HumanWalk(819),
	Woodcutting(2117),
	HalfLayingDown(1147),
	Sitting(4114),
	CurledUp(4712),

	// Actions
	Grabbing(551),
	Eat(829),
	RangeCook(896),
	Alching(713),
	FireCook(897),
	FurnaceSmelt(899),
	HerbloreMix(363),
	Fletching(1248),
	AnvilBang(898),
	Crying(860),
	Mining(823),

	ChildStarJump(218),
	ChildPlay1(6484),
	ChildPlay2(6485),
	ChildWalk(189),
	ChildIdle(195),

	// Non-human
	FireIdle(475),
	CatLunge(319),
	CatSit(2134),
	RatIdle(2704),
	RatBanging(2706),

	DwarfLean(6206),
	DwarfWalk(98),
	DwarfMining(99),
	DwarfIdle(101),

	GoblinPull(3387),

	// ???
	FrontalGrab(897),
	ChestRub(190);

	private final Integer id;

	AnimationID(Integer id) {
		this.id = id;
	}

	public Integer getId() {
		return this.id;
	}
}
