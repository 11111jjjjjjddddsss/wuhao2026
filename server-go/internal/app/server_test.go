package app

import "testing"

func TestValidateRoundCompleteInputMatchesCurrentRules(t *testing.T) {
	cases := []struct {
		name          string
		clientMsgID   string
		userText      string
		userImages    []string
		assistantText string
		want          string
	}{
		{
			name:          "text round allowed",
			clientMsgID:   "msg-1",
			userText:      "hello",
			assistantText: "world",
			want:          "",
		},
		{
			name:          "image only round allowed",
			clientMsgID:   "msg-2",
			userImages:    []string{"https://img/current.jpg"},
			assistantText: "world",
			want:          "",
		},
		{
			name:          "reject empty user input",
			clientMsgID:   "msg-3",
			assistantText: "world",
			want:          "user_text or user_images required",
		},
		{
			name:        "reject too many images",
			clientMsgID: "msg-4",
			userImages: []string{
				"https://img/1.jpg",
				"https://img/2.jpg",
				"https://img/3.jpg",
				"https://img/4.jpg",
				"https://img/5.jpg",
			},
			assistantText: "world",
			want:          "single request supports up to 4 images",
		},
		{
			name:        "reject empty assistant text",
			clientMsgID: "msg-5",
			userText:    "hello",
			want:        "assistant_text required",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := validateRoundCompleteInput(tc.clientMsgID, tc.userText, tc.userImages, tc.assistantText); got != tc.want {
				t.Fatalf("validation mismatch: got %q want %q", got, tc.want)
			}
		})
	}
}
