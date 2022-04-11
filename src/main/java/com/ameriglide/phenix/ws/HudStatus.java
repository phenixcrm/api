package com.ameriglide.phenix.ws;

import com.ameriglide.phenix.types.CallDirection;

class HudStatus {

  public boolean available;
  public CallDirection direction;
  public String callId;

  public void clear() {
    available = false;
    callId = null;
    direction = null;
  }

}
