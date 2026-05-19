export interface CreateWishItemRequest {
  name: string;
  price: number;
  imageUrl?: string;
}

export interface UpdateWishItemRequest {
  name?: string;
  price?: number;
  imageUrl?: string;
  isRedeemed?: boolean;
  redeemedDate?: string;
}

export interface WishItemResponse {
  id: number;
  name: string;
  price: number;
  imageUrl: string | null;
  isRedeemed: boolean;
  redeemedDate: string | null;
}
