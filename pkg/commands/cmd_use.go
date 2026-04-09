package commands

func useCommand() Definition {
	return Definition{
		Name:        "use",
		Description: "Принудительно применить установленный навык к одному запросу",
		Usage:       "/use <skill> [message]",
	}
}
